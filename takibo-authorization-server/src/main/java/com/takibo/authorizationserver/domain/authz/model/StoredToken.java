package com.takibo.authorizationserver.domain.authz.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record StoredToken(
        String value,
        String hash,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        Map<String, Object> metadata,
        String tokenType,
        String scopes
) {
    public StoredToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Token value cannot be null or blank");
        }
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Token hash cannot be null or blank");
        }
        if (!hash.matches("^[a-f0-9]{64}$")) {
            throw new IllegalArgumentException("Token hash must be 64-char hex lowercase");
        }
    }
}
