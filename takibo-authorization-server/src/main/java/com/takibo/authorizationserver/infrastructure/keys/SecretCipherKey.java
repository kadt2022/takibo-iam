package com.takibo.authorizationserver.infrastructure.keys;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Une cle de chiffrement au repos, et l'identifiant sous lequel elle sera reconnue.
 * <p>
 * L'identifiant n'est pas cosmetique : il est inscrit dans chaque chiffre produit. C'est lui
 * qui rend la <b>rotation de la cle de chiffrement</b> possible. Sans lui, on ne saurait pas
 * quelle cle a scelle quelle ligne : changer de cle imposerait de tout redechiffrer et
 * rechiffrer d'un seul tenant, ou de ne jamais en changer. Avec lui, l'ancienne cle reste
 * acceptee en lecture pendant que la nouvelle chiffre, et les lignes migrent a leur rythme.
 * <p>
 * Il sert aussi apres coup : si une cle fuit, l'identifiant dit exactement quelles lignes
 * sont concernees.
 *
 * @param id       identifiant stable, {@code [A-Za-z0-9_-]{1,64}}. Jamais reutilise pour une
 *                 autre matiere : deux cles de meme identifiant rendraient le chiffre
 *                 indechiffrable.
 * @param material matiere brute, exactement 32 octets (AES-256). Dans un meme trousseau,
 *                 elle ne peut pas etre reutilisee sous un autre identifiant : une rotation
 *                 doit changer la cle, pas seulement son etiquette.
 */
public record SecretCipherKey(String id, byte[] material) {

    static final int REQUIRED_MATERIAL_LENGTH_BYTES = 32;

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public SecretCipherKey {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            // L'identifiant voyage dans le chiffre, separe par '$' : il ne peut contenir ni
            // separateur, ni caractere qui rendrait le format ambigu.
            throw new IllegalArgumentException("SECRET_CIPHER_KEY_ID_INVALID");
        }
        if (material == null || material.length != REQUIRED_MATERIAL_LENGTH_BYTES) {
            // Exactement 32, pas « au moins 32 ». JCE n'accepte que 16, 24 ou 32 octets : une
            // matiere de 33 construirait l'objet sans broncher puis ferait echouer chaque
            // chiffrement. TAS impose AES-256, donc 16 et 24 sont refuses aussi.
            throw new IllegalArgumentException(
                    "SECRET_CIPHER_KEY_MUST_BE_" + REQUIRED_MATERIAL_LENGTH_BYTES + "_BYTES");
        }
        material = material.clone();
    }

    /** Copie defensive : la matiere ne doit pas pouvoir etre alteree apres construction. */
    @Override
    public byte[] material() {
        return material.clone();
    }

    /** Volontairement muet : la matiere ne doit jamais atterrir dans un log. */
    @Override
    public String toString() {
        return "SecretCipherKey[id=" + id + "]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SecretCipherKey key
                && id.equals(key.id)
                && Arrays.equals(material, key.material);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + Arrays.hashCode(material);
    }
}
