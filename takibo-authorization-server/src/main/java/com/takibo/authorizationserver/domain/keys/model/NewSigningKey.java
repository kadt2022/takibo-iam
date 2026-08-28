package com.takibo.authorizationserver.domain.keys.model;

import java.util.Map;

/**
 * Une clé de signature de plateforme prête à être activée (TAS-GRANTS-02A, rotation).
 * <p>
 * Porte déjà sa matière privée chiffrée : le chiffrement est fait avant d'atteindre
 * l'écriture, jamais après, pour que la matière en clair n'ait aucune raison de traverser la
 * couche de persistance.
 *
 * @param publicJwkJson       forme JSON publique, identique à celle dont dérive la matière
 *                            privée — {@code PersistentJwkSource} confronte les deux à la
 *                            lecture, donc toute divergence introduite ici serait détectée,
 *                            mais bloquerait le démarrage plutôt que de le prévenir ici.
 * @param privateKeyEncrypted forme scellée par {@code SecretCipher}, prête pour
 *                            {@code private_key_encrypted}
 */
public record NewSigningKey(
        String kid,
        String alg,
        String kty,
        String keyUse,
        Map<String, Object> publicJwkJson,
        String privateKeyEncrypted
) {

    public NewSigningKey {
        requireText(kid, "NEW_SIGNING_KEY_REQUIRES_KID");
        requireText(alg, "NEW_SIGNING_KEY_REQUIRES_ALG");
        requireText(kty, "NEW_SIGNING_KEY_REQUIRES_KTY");
        requireText(keyUse, "NEW_SIGNING_KEY_REQUIRES_KEY_USE");
        requireText(privateKeyEncrypted, "NEW_SIGNING_KEY_REQUIRES_ENCRYPTED_MATERIAL");
        if (publicJwkJson == null || publicJwkJson.isEmpty()) {
            throw new IllegalArgumentException("NEW_SIGNING_KEY_REQUIRES_PUBLIC_JWK");
        }
        publicJwkJson = Map.copyOf(publicJwkJson);
    }

    private static void requireText(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(code);
        }
    }
}
