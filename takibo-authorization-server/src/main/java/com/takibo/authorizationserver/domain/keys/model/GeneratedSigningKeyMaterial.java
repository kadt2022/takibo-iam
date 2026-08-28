package com.takibo.authorizationserver.domain.keys.model;

import java.util.Map;

/**
 * Matière de signature fraîchement générée, avant chiffrement (TAS-GRANTS-02A, rotation).
 * <p>
 * Type de domaine, délibérément sans dépendance à Nimbus ni à aucune bibliothèque JOSE :
 * {@link com.takibo.authorizationserver.domain.keys.port.SigningKeyMaterialGenerator} est le
 * seul point par lequel une implémentation concrète (RSA local aujourd'hui, KMS ou HSM demain)
 * entre dans le domaine, et elle ne peut le faire qu'à travers cette forme neutre.
 *
 * @param kid               identifiant de la clé
 * @param alg               algorithme de signature, par exemple {@code RS256}
 * @param kty               type de clé, par exemple {@code RSA}
 * @param keyUse            usage, par exemple {@code sig}
 * @param publicJwkJson     forme JSON publique, sans aucun paramètre privé
 * @param privateKeyMaterial forme sérialisée complète, matière privée incluse — c'est ce que
 *                          {@link com.takibo.authorizationserver.domain.keys.SigningKeyRotationService}
 *                          chiffre avant écriture, sans jamais en garder le clair au-delà
 */
public record GeneratedSigningKeyMaterial(
        String kid,
        String alg,
        String kty,
        String keyUse,
        Map<String, Object> publicJwkJson,
        String privateKeyMaterial
) {

    public GeneratedSigningKeyMaterial {
        requireText(kid, "GENERATED_SIGNING_KEY_REQUIRES_KID");
        requireText(alg, "GENERATED_SIGNING_KEY_REQUIRES_ALG");
        requireText(kty, "GENERATED_SIGNING_KEY_REQUIRES_KTY");
        requireText(keyUse, "GENERATED_SIGNING_KEY_REQUIRES_KEY_USE");
        requireText(privateKeyMaterial, "GENERATED_SIGNING_KEY_REQUIRES_PRIVATE_MATERIAL");
        if (publicJwkJson == null || publicJwkJson.isEmpty()) {
            throw new IllegalArgumentException("GENERATED_SIGNING_KEY_REQUIRES_PUBLIC_JWK");
        }
        publicJwkJson = Map.copyOf(publicJwkJson);
    }

    private static void requireText(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(code);
        }
    }
}
