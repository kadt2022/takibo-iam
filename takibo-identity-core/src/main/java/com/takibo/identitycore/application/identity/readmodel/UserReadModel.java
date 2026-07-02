package com.takibo.identitycore.application.identity.readmodel;

import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue de lecture user + email du compte, produite par une projection JPA
 * (join {@code users -> accounts}). L'aggregate {@code User} ne porte pas
 * {@code Account} : le read-side a son propre modèle.
 */
public record UserReadModel(
        UUID id,
        UUID spaceId,
        UUID accountId,
        String email,
        String username,
        String firstName,
        String lastName,
        UserStatus status,
        UserType type,
        boolean mfaEnabled,
        boolean passwordExpired,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
