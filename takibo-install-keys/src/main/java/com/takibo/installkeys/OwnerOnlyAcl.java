package com.takibo.installkeys;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;

/**
 * Restreint un fichier à son seul propriétaire, puis vérifie que le système de fichiers a
 * obéi (TAKIBO-INSTALL-KEYS-01).
 * <p>
 * Séparé de {@code SecretFileWriter} pour une raison qui n'est pas cosmétique : cette logique
 * ne s'exécute que sur les systèmes à ACL, et la CI tourne sous Linux. Laissée à l'intérieur
 * du writer, elle n'aurait jamais été exercée que sur le poste d'un développeur Windows —
 * c'est-à-dire nulle part de façon fiable. Ici, elle reçoit la vue et le propriétaire en
 * paramètres, donc un test la couvre sur n'importe quel système.
 * <p>
 * La vérification est le cœur de l'affaire. {@code setAcl} peut être partiellement honoré
 * selon le volume, et une entrée héritée laissée en place — {@code Administrators},
 * {@code SYSTEM}, {@code Users} — rendrait le secret lisible par d'autres sans qu'aucune
 * exception ne soit levée. On relit donc, et on refuse plutôt que d'espérer.
 */
final class OwnerOnlyAcl {

    private OwnerOnlyAcl() {
    }

    /**
     * @param view  la vue ACL du fichier à restreindre
     * @param owner le principal qui doit rester seul autorisé
     * @param file  nommé uniquement pour le diagnostic — jamais lu ni écrit ici
     * @throws SecretFileException si une entrée subsiste pour un autre principal, ou une
     *                             entrée de refus qui rendrait le comportement dépendant de
     *                             l'ordre des ACE
     */
    static void restrict(AclFileAttributeView view, UserPrincipal owner, Path file)
            throws IOException {
        view.setAcl(List.of(AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build()));

        List<AclEntry> applied = view.getAcl();
        boolean ownerOnly = !applied.isEmpty() && applied.stream().allMatch(
                entry -> entry.principal().equals(owner) && entry.type() == AclEntryType.ALLOW);
        if (!ownerOnly) {
            throw new SecretFileException(ExitCode.UNSAFE_FILESYSTEM,
                    "This filesystem kept access entries for other principals; "
                            + "refusing to write secrets to " + file);
        }
    }
}
