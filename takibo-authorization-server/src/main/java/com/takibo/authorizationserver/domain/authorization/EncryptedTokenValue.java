package com.takibo.authorizationserver.domain.authorization;

import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import com.takibo.authorizationserver.domain.keys.port.SecretDecryptionException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Ce qu'une colonne {@code *_value}/{@code *_hash} de {@code oauth2_authorization} porte pour
 * un token ou un code (TAS-GRANTS-02) : la valeur chiffrée, récupérable, et son hash de
 * recherche — jamais la valeur en clair, jamais un hash seul.
 * <p>
 * {@link #seal} et {@link #reveal} sont les deux seuls points d'entrée : {@code seal} au
 * moment d'écrire une valeur reçue de Spring Authorization Server, {@code reveal} au moment
 * de la lui restituer après une recherche par le même hash. Le {@link SecretContext} doit
 * être reconstruit identique à celui utilisé pour sceller —
 * {@link SecretContext#oauth2AuthorizationValue} le lie à la ligne et à la colonne précises,
 * pas seulement à la ligne : voir sa javadoc pour ce que ça empêche.
 * <p>
 * La fonction de hash n'est pas figée à {@link TokenHash#sha256Hex} : {@code user_code} est
 * une valeur de faible entropie (quelques caractères humainement saisissables), qu'un SHA-256
 * non clé rendrait énumérable hors ligne après la seule fuite de la colonne de hash — sans
 * même casser le chiffrement. Passer un HMAC d'installation pour ce cas précis ferme cette
 * attaque ; les cinq autres colonnes (codes et tokens à haute entropie) restent sur
 * {@link TokenHash#sha256Hex} via les surcharges à deux arguments.
 *
 * @param encryptedValue chiffre autoportant produit par {@link SecretCipher#encrypt}, stockable
 *                        tel quel dans la colonne {@code *_value}
 * @param hash            hash du clair d'origine produit par la fonction fournie à
 *                        {@link #seal}, stockable tel quel dans la colonne {@code *_hash}
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

    /** Équivalent à {@link #seal(SecretCipher, SecretContext, String, UnaryOperator)} avec SHA-256. */
    public static EncryptedTokenValue seal(SecretCipher cipher, SecretContext context, String plaintext) {
        return seal(cipher, context, plaintext, TokenHash::sha256Hex);
    }

    /**
     * Chiffre {@code plaintext} et calcule son hash de recherche.
     *
     * @param cipher    port de chiffrement (TAS-GRANTS-02A)
     * @param context   lie le chiffre à la ligne et à la colonne précises — voir
     *                  {@link SecretContext#oauth2AuthorizationValue}
     * @param plaintext le token ou le code tel que produit par Spring Authorization Server
     * @param hasher    calcule le hash de recherche à partir du clair — {@link TokenHash#sha256Hex}
     *                  pour un code/token à haute entropie, un HMAC d'installation pour
     *                  {@code user_code}
     */
    public static EncryptedTokenValue seal(
            SecretCipher cipher, SecretContext context, String plaintext, UnaryOperator<String> hasher) {
        Objects.requireNonNull(cipher, "cipher must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        Objects.requireNonNull(hasher, "hasher must not be null");
        return new EncryptedTokenValue(
                cipher.encrypt(context, plaintext),
                hasher.apply(plaintext));
    }

    /** Équivalent à {@link #reveal(SecretCipher, SecretContext, UnaryOperator)} avec SHA-256. */
    public String reveal(SecretCipher cipher, SecretContext context) {
        return reveal(cipher, context, TokenHash::sha256Hex);
    }

    /**
     * Restitue le clair d'origine.
     * <p>
     * {@link SecretCipher#decrypt} authentifie déjà le chiffre lui-même (GCM) et le lie à sa
     * ligne et à sa colonne via l'AAD du {@link SecretContext} — mais rien, à ce stade, ne lie
     * le clair obtenu au {@link #hash} stocké à côté de {@link #encryptedValue}. Si les deux
     * colonnes divergent — corruption, migration incomplète, ou une ligne dont {@code *_hash}
     * a été réécrit pour rediriger une recherche par hash vers un chiffre qui reste par
     * ailleurs valide pour son propre emplacement — {@code reveal} recalcule le hash du clair
     * déchiffré avec la même fonction qu'à {@link #seal} et le compare, en temps constant, à
     * celui stocké : sans cette vérification, le couple {@code (encryptedValue, hash)} n'est
     * qu'une convention, pas un invariant vérifié.
     *
     * @param context exactement celui fourni à {@link #seal} — même ligne, même colonne
     * @param hasher  exactement celle fournie à {@link #seal} — un hash recalculé avec une
     *                autre fonction ne correspondrait jamais, quelle que soit la validité du
     *                clair
     * @throws SecretDecryptionException si le chiffre est illisible, altéré, scellé pour un
     *         autre contexte, ou si le clair obtenu ne correspond pas à {@link #hash}
     */
    public String reveal(SecretCipher cipher, SecretContext context, UnaryOperator<String> hasher) {
        Objects.requireNonNull(cipher, "cipher must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(hasher, "hasher must not be null");
        String plaintext = cipher.decrypt(context, encryptedValue);
        String recomputedHash = hasher.apply(plaintext);
        if (!MessageDigest.isEqual(
                hash.getBytes(StandardCharsets.UTF_8),
                recomputedHash.getBytes(StandardCharsets.UTF_8))) {
            // Meme message opaque que SecretCipher pour un chiffre altere : la cause exacte
            // n'apprendrait rien a un exploitant et renseignerait un attaquant.
            throw new SecretDecryptionException("ENCRYPTED_TOKEN_VALUE_HASH_MISMATCH");
        }
        return plaintext;
    }
}
