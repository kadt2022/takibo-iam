package com.takibo.installkeys;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Écrit un secret sur le disque, ou refuse (TAKIBO-INSTALL-KEYS-01).
 *
 * <h2>La séquence, et ce que chaque étape empêche</h2>
 * <ol>
 *   <li><b>Interroger le système de fichiers</b> — ses capacités réelles, jamais le nom du
 *       système d'exploitation. Un partage réseau monté sous Linux peut ne pas porter les
 *       permissions POSIX, et un montage sous Windows peut ne pas porter d'ACL : c'est le
 *       {@code FileStore} qui répond, pas {@code os.name}.</li>
 *   <li><b>Créer un fichier temporaire restreint dans le répertoire cible</b> — dans ce
 *       répertoire précisément, car la publication finale exige le même volume.</li>
 *   <li><b>Écrire et forcer sur le disque</b>, temporaire toujours restreint.</li>
 *   <li><b>Publier par lien</b> — voir ci-dessous.</li>
 *   <li><b>Effacer le temporaire</b>, y compris en cas d'échec : un secret orphelin dans un
 *       répertoire est un secret exposé.</li>
 * </ol>
 *
 * <h2>Pourquoi un lien, et non un déplacement</h2>
 * Il n'existe pas, dans l'API standard, de déplacement à la fois atomique et non destructeur.
 * {@code Files.move} avec {@code ATOMIC_MOVE} appelle {@code rename}, qui <b>écrase</b>
 * silencieusement une cible existante ; sans {@code ATOMIC_MOVE}, l'implémentation vérifie
 * l'existence puis renomme, laissant une fenêtre entre le test et l'écriture — exactement la
 * course que ce récit refuse.
 * <p>
 * {@link Files#createLink} n'a pas ce défaut : le lien est créé ou échoue, en une seule
 * opération, et il <b>échoue</b> si la cible existe. Le fichier publié est le même inode que le
 * temporaire, donc il porte déjà ses permissions restreintes et son contenu complet : rien
 * n'est jamais visible sous le nom final dans un état partiel. Le temporaire est ensuite
 * effacé, et il ne reste qu'un fichier.
 * <p>
 * Si le volume ne sait pas créer de lien, la publication n'est pas garantie : refus, plutôt
 * qu'un repli sur une séquence dont on sait qu'elle peut écraser.
 *
 * <h2>Le secret n'existe jamais sans protection</h2>
 * Sous POSIX, les permissions sont fournies comme attributs <b>de création</b>. Sous Windows,
 * l'API standard n'offre aucun attribut de création ACL : le fichier est alors créé
 * <b>vide</b>, son ACL est restreinte, puis relue pour vérification, et ce n'est qu'ensuite
 * que le contenu est écrit. La distinction est ce qui compte : ce qui existe brièvement sous
 * les permissions héritées est un fichier vide, jamais un secret. Si la vérification échoue,
 * le fichier vide est effacé et rien n'est publié.
 */
final class SecretFileWriter {

    private static final String TEMP_PREFIX = "takibo-secrets-";
    private static final String TEMP_SUFFIX = ".tmp";

    private SecretFileWriter() {
    }

    /**
     * @param target chemin final, qui ne doit pas exister
     * @param content contenu complet à écrire
     * @throws SecretFileException porteur du code de sortie décrivant le refus
     */
    static void write(Path target, String content) {
        Path directory = parentOf(target);
        Restriction restriction = Restriction.of(directory);

        // Refus precoce : sans valeur de garantie — la course reste possible jusqu'a la
        // publication — mais il evite de generer un temporaire pour rien et rend le diagnostic
        // immediat dans le cas courant d'une seconde execution.
        if (Files.exists(target)) {
            throw targetExists(target);
        }

        Path temporary = restriction.createRestrictedFile(directory);
        try {
            writeFully(temporary, content);
            publish(temporary, target);
        } catch (SecretFileException e) {
            deleteQuietly(temporary);
            throw e;
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw new SecretFileException(ExitCode.IO_FAILURE,
                    "Failed to write the secrets file: " + target, e);
        }
        deleteQuietly(temporary);
    }

    /**
     * Publication : le lien porte le nom final, ou rien ne se passe. Aucune vérification
     * d'existence préalable — c'est le système de fichiers qui arbitre, dans la même logique
     * que l'{@code ON CONFLICT} de l'amorçage des clés de signature.
     */
    private static void publish(Path temporary, Path target) throws IOException {
        try {
            Files.createLink(target, temporary);
        } catch (FileAlreadyExistsException e) {
            throw targetExists(target);
        } catch (UnsupportedOperationException e) {
            throw new SecretFileException(ExitCode.UNSAFE_FILESYSTEM,
                    "This filesystem cannot publish a file atomically without replacing an "
                            + "existing one; refusing to write secrets to " + target, e);
        }
    }

    private static void writeFully(Path temporary, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.write(java.nio.ByteBuffer.wrap(bytes));
            // Forcer avant de publier : le nom final ne doit jamais designer un contenu que le
            // disque n'a pas encore recu.
            channel.force(true);
        }
    }

    private static Path parentOf(Path target) {
        Path directory = target.toAbsolutePath().getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new SecretFileException(ExitCode.IO_FAILURE,
                    "The output directory does not exist: " + directory);
        }
        return directory;
    }

    private static SecretFileException targetExists(Path target) {
        return new SecretFileException(ExitCode.TARGET_EXISTS,
                "Refusing to overwrite an existing secrets file: " + target);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Le temporaire est deja publie ou deja parti ; signaler cet echec masquerait la
            // cause reelle, et il n'y a rien a corriger ici.
        }
    }

    /**
     * La façon dont ce système de fichiers sait restreindre un fichier — déduite de ses
     * capacités déclarées, jamais du système d'exploitation.
     */
    private enum Restriction {

        /** Permissions fournies à la création : le fichier n'existe jamais autrement. */
        POSIX {
            @Override
            Path createRestrictedFile(Path directory) {
                Set<PosixFilePermission> ownerOnly =
                        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                FileAttribute<Set<PosixFilePermission>> attribute =
                        PosixFilePermissions.asFileAttribute(ownerOnly);
                try {
                    return Files.createTempFile(directory, TEMP_PREFIX, TEMP_SUFFIX, attribute);
                } catch (IOException e) {
                    throw new SecretFileException(ExitCode.IO_FAILURE,
                            "Failed to create a restricted temporary file in " + directory, e);
                }
            }
        },

        /** ACL posée sur un fichier vide, puis relue : aucun secret n'existe entre les deux. */
        WINDOWS_ACL {
            @Override
            Path createRestrictedFile(Path directory) {
                Path temporary;
                try {
                    temporary = Files.createTempFile(directory, TEMP_PREFIX, TEMP_SUFFIX);
                } catch (IOException e) {
                    throw new SecretFileException(ExitCode.IO_FAILURE,
                            "Failed to create a temporary file in " + directory, e);
                }
                try {
                    restrictToOwner(temporary);
                    return temporary;
                } catch (IOException | RuntimeException e) {
                    deleteQuietly(temporary);
                    throw e instanceof SecretFileException secretFileException
                            ? secretFileException
                            : new SecretFileException(ExitCode.UNSAFE_FILESYSTEM,
                                    "Failed to restrict the temporary file to its owner in "
                                            + directory, e);
                }
            }

            private void restrictToOwner(Path file) throws IOException {
                AclFileAttributeView acl =
                        Files.getFileAttributeView(file, AclFileAttributeView.class);
                UserPrincipal owner = Files.getOwner(file);
                acl.setAcl(List.of(AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(owner)
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                        .build()));

                // Relire plutot que faire confiance : setAcl peut etre partiellement honore
                // selon le volume, et une entree heritee laissee en place rendrait le secret
                // lisible par d'autres.
                List<AclEntry> applied = acl.getAcl();
                boolean ownerOnly = applied.stream()
                        .allMatch(entry -> entry.principal().equals(owner)
                                && entry.type() == AclEntryType.ALLOW);
                if (!ownerOnly) {
                    throw new SecretFileException(ExitCode.UNSAFE_FILESYSTEM,
                            "This filesystem kept access entries for other principals; "
                                    + "refusing to write secrets in " + file.getParent());
                }
            }
        };

        abstract Path createRestrictedFile(Path directory);

        static Restriction of(Path directory) {
            if (supports(directory, "posix")) {
                return POSIX;
            }
            if (supports(directory, "acl")) {
                return WINDOWS_ACL;
            }
            throw new SecretFileException(ExitCode.UNSAFE_FILESYSTEM,
                    "This filesystem supports neither POSIX permissions nor ACLs; "
                            + "refusing to write secrets in " + directory);
        }

        /**
         * Interroge le volume qui porte ce répertoire, et non le système de fichiers par
         * défaut : c'est le montage qui décide, et deux répertoires de la même machine
         * peuvent répondre différemment.
         */
        private static boolean supports(Path directory, String view) {
            try {
                return Files.getFileStore(directory).supportsFileAttributeView(view);
            } catch (IOException e) {
                throw new SecretFileException(ExitCode.IO_FAILURE,
                        "Failed to inspect the filesystem hosting " + directory, e);
            }
        }
    }
}
