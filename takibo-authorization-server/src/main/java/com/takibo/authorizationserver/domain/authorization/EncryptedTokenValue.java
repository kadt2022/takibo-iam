package com.takibo.authorizationserver.domain.authorization;

import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Ce qu'une colonne {@code *_value}/{@code *_hash} de {@code oauth2_authorization} porte pour
 * un token ou un code (TAS-GRANTS-02) : la valeur chiffrée, récupérable, et son hash de
 * recherche — jamais la valeur en clair, jamais un hash seul.
 * <p>
 * {@link #seal} et {@link #reveal} sont les deux seuls points d'entrée : {@code seal} au
 * moment d'écrire une valeur reçue de Spring Authorization Server, {@code reveal} au moment
 * de la lui restituer après une recherche par {@link TokenHash#sha256Hex}. Le
 * {@link SecretContext} doit être reconstruit identique à celui utilisé pour sceller —
 * {@link SecretContext#oauth2AuthorizationValue} le lie à la ligne et à la colonne précises,
 * pas seulement à la ligne : voir sa javadoc pour ce que ça empêche.
 *
 * @param encryptedValue chiffre autoportant produit par {@link SecretCipher#encrypt}, stockable
 *                        tel quel dans la colonne {@code *_value}
 * @param hash            {@link TokenHash#sha256Hex} du clair d'origine, stockable tel quel
 *                        dans la colonne {@code *_hash}
 */
public record EncryptedTokenValue(String encryptedValue, String hash) {

    private static final Pattern HASH_FORMAT = Pattern.compile("^[a-f0-9]{64}$");

    public EncryptedTokenValue {
        Objects.requireNonNull(encryptedValue, "encryptedValue must not be null");
        Objects.requireNonNull(hash, "hash must not be null");
        if (!HASH_FORMAT.matcher(hash).matches()) {
            // Doit rester coherent avec les contraintes CHECK de V202601091233 : un hash qui
            // ne matcherait pas cette forme provoquerait un rejet en base de toute facon, mais
            // plus tard et avec un message SQL opaque plutot qu'un echec immediat et lisible.
            throw new IllegalArgumentException("HASH_MUST_BE_64_LOWERCASE_HEX_CHARS");
        }
    }

    /**
     * Chiffre {@code plaintext} et calcule son hash de recherche.
     *
     * @param cipher    port de chiffrement (TAS-GRANTS-02A)
     * @param context   lie le chiffre à la ligne et à la colonne précises — voir
     *                  {@link SecretContext#oauth2AuthorizationValue}
     * @param plaintext le token ou le code tel que produit par Spring Authorization Server
     */
    public static EncryptedTokenValue seal(SecretCipher cipher, SecretContext context, String plaintext) {
        Objects.requireNonNull(cipher, "cipher must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        return new EncryptedTokenValue(
                cipher.encrypt(context, plaintext),
                TokenHash.sha256Hex(plaintext));
    }

    /**
     * Restitue le clair d'origine.
     *
     * @param context exactement celui fourni à {@link #seal} — même ligne, même colonne
     * @throws com.takibo.authorizationserver.domain.keys.port.SecretDecryptionException si le
     *         chiffre est illisible, altéré, ou scellé pour un autre contexte
     */
    public String reveal(SecretCipher cipher, SecretContext context) {
        Objects.requireNonNull(cipher, "cipher must not be null");
        Objects.requireNonNull(context, "context must not be null");
        return cipher.decrypt(context, encryptedValue);
    }
}
