package com.takibo.authorizationserver.infrastructure.keys;

import com.nimbusds.jose.jwk.JWK;

/**
 * Matière RSA fraîchement générée, avant chiffrement (TAS-GRANTS-02A, rotation).
 * <p>
 * Enveloppe minimale : le seul rôle de ce type est de traverser
 * {@link RsaSigningKeyGenerator} jusqu'au service de rotation, qui chiffre la partie privée
 * et n'en garde jamais le clair au-delà de cet appel.
 *
 * @param privateJwk le JWK complet, matière privée incluse — {@code privateJwk.toPublicJWK()}
 *                   donne la forme à publier
 */
public record GeneratedSigningKeyMaterial(JWK privateJwk) {

    public GeneratedSigningKeyMaterial {
        if (privateJwk == null || !privateJwk.isPrivate()) {
            throw new IllegalArgumentException("GENERATED_SIGNING_KEY_MUST_CARRY_PRIVATE_MATERIAL");
        }
    }
}
