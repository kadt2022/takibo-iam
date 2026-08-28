package com.takibo.authorizationserver.domain.keys.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * @param expiresAt    fin de la période de validité de la clé (cryptopériode) — distincte de
 *                     {@code publishUntil}, qui gouverne la publication d'une clé retirée
 * @param publishUntil fin de publication dans le JWKS pour une clé retirée ; {@code null} pour
 *                     une clé active, qui n'a pas de borne de publication
 */
public record TasSigningKey(
        UUID id,
        UUID orgId,

        String kid,
        String alg,
        String kty,
        String keyUse,
        boolean issuer,
        KeyStatus status,

        Map<String, Object> publicJwkJson,
        String privateKeyEncrypted,

        OffsetDateTime notBefore,
        OffsetDateTime expiresAt,
        OffsetDateTime publishUntil,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
