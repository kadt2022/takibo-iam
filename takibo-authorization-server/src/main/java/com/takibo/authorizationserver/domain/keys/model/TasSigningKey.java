package com.takibo.authorizationserver.domain.keys.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

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

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
