package com.takibo.managementservice.domain.model;

import java.time.Instant;

/**
 * Secret client généré (plain pour retour one-shot, hash pour stockage) + date d'expiration.
 */
public record Secrets(
    String plain,
    String hash,
    Instant expiresAt
) {
    public static Secrets none() {
        return new Secrets(null, null, null);
    }
}
