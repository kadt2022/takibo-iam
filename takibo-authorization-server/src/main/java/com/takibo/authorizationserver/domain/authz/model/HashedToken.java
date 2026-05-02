package com.takibo.authorizationserver.domain.authz.model;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Hash-only token (no plaintext value stored).
 *
 * Used for:
 * - Authorization codes (single-use, short-lived)
 * - Device codes (OAuth2 Device Flow)
 * - User codes (OAuth2 Device Flow)
 *
 * Security: Only hash is stored in DB for lookup.
 * The plaintext value is never persisted (hash-only storage).
 */
public record HashedToken(
        String hash,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        Map<String, Object> metadata
) {
    public HashedToken {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Token hash cannot be null or blank");
        }
        if (!hash.matches("^[a-f0-9]{64}$")) {
            throw new IllegalArgumentException("Token hash must be 64-char hex lowercase");
        }
    }
}
