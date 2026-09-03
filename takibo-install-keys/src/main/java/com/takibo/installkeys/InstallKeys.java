package com.takibo.installkeys;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/**
 * Les trois valeurs que l'installation doit produire une fois, et une seule
 * (TAKIBO-INSTALL-KEYS-01).
 * <p>
 * Ce sont les <b>secrets externes</b> de TAKIBO : ceux que le client range dans son coffre,
 * sauvegarde et fait tourner. La clé de signature RSA n'en fait pas partie — TAS l'amorce
 * lui-même au premier démarrage et la conserve chiffrée en base avec la clé AES ci-dessous
 * (TAS-KEYS-BOOTSTRAP-01). Trois valeurs, donc, jamais quatre.
 *
 * <h2>Deux matières tirées indépendamment</h2>
 * La clé de chiffrement et la clé HMAC ne sont pas dérivées l'une de l'autre : réutiliser la
 * même matière pour chiffrer et pour authentifier annulerait la séparation de rôles que le
 * HMAC introduit. Deux tirages distincts du même générateur, et une vérification que le
 * hasard n'a pas rendu les deux identiques — événement d'une improbabilité telle que sa
 * survenue dénoncerait un générateur cassé, jamais de la malchance.
 *
 * @param cipherKeyId           identifiant de la clé de chiffrement, {@code k-<UUID>}
 * @param cipherKeyMaterial     32 octets pour AES-256
 * @param userCodeHmacMaterial  32 octets pour HMAC-SHA256, distincts des précédents
 */
public record InstallKeys(String cipherKeyId, byte[] cipherKeyMaterial,
                          byte[] userCodeHmacMaterial) {

    /** AES-256 et HMAC-SHA256 : la même longueur, pour deux usages qui ne se mélangent pas. */
    static final int KEY_LENGTH_BYTES = 32;

    /**
     * Préfixe de l'identifiant de clé. Volontairement sans composante temporelle :
     * l'identifiant reste opaque, et l'ordre des clés doit se lire dans les métadonnées de la
     * base, jamais dans une chaîne de caractères que rien ne garantit.
     */
    static final String KEY_ID_PREFIX = "k-";

    public InstallKeys {
        requireLength(cipherKeyMaterial, "CIPHER_KEY");
        requireLength(userCodeHmacMaterial, "USER_CODE_HMAC_KEY");
        if (Arrays.equals(cipherKeyMaterial, userCodeHmacMaterial)) {
            throw new IllegalStateException("INSTALL_KEYS_MUST_NOT_SHARE_THE_SAME_MATERIAL");
        }
        cipherKeyMaterial = cipherKeyMaterial.clone();
        userCodeHmacMaterial = userCodeHmacMaterial.clone();
    }

    /**
     * Tire les trois valeurs d'une source cryptographiquement sûre.
     *
     * @param random passé en paramètre plutôt que créé ici — c'est ce qui rend le tirage
     *               observable par test sans qu'aucune graine fixe n'existe en production
     */
    public static InstallKeys generate(SecureRandom random) {
        byte[] cipherKey = new byte[KEY_LENGTH_BYTES];
        byte[] hmacKey = new byte[KEY_LENGTH_BYTES];
        random.nextBytes(cipherKey);
        random.nextBytes(hmacKey);
        return new InstallKeys(KEY_ID_PREFIX + UUID.randomUUID(), cipherKey, hmacKey);
    }

    /** Forme attendue par la configuration : la matière voyage encodée en base64. */
    public String cipherKeyBase64() {
        return Base64.getEncoder().encodeToString(cipherKeyMaterial);
    }

    public String userCodeHmacKeyBase64() {
        return Base64.getEncoder().encodeToString(userCodeHmacMaterial);
    }

    /** Copie défensive : la matière ne doit pas pouvoir être altérée après construction. */
    @Override
    public byte[] cipherKeyMaterial() {
        return cipherKeyMaterial.clone();
    }

    @Override
    public byte[] userCodeHmacMaterial() {
        return userCodeHmacMaterial.clone();
    }

    /**
     * Un record compare ses composants par référence lorsqu'ils sont des tableaux : deux
     * instances portant la même matière seraient déclarées différentes, et une même instance
     * dupliquée le serait aussi. La comparaison porte donc sur le contenu.
     * <p>
     * {@link java.util.Arrays#equals(byte[], byte[])} n'est pas à temps constant : c'est sans
     * conséquence ici, où l'on compare deux valeurs que l'on possède déjà toutes les deux,
     * jamais un secret contre une saisie d'attaquant.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof InstallKeys keys
                && cipherKeyId.equals(keys.cipherKeyId)
                && Arrays.equals(cipherKeyMaterial, keys.cipherKeyMaterial)
                && Arrays.equals(userCodeHmacMaterial, keys.userCodeHmacMaterial);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * cipherKeyId.hashCode() + Arrays.hashCode(cipherKeyMaterial))
                + Arrays.hashCode(userCodeHmacMaterial);
    }

    /**
     * Volontairement muet sur la matière : ce type ne doit jamais pouvoir être journalisé par
     * inadvertance, et l'implémentation par défaut d'un record imprimerait ses composants.
     */
    @Override
    public String toString() {
        return "InstallKeys[cipherKeyId=" + cipherKeyId + "]";
    }

    private static void requireLength(byte[] material, String name) {
        if (material == null || material.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "INSTALL_" + name + "_MUST_BE_" + KEY_LENGTH_BYTES + "_BYTES");
        }
    }
}
