package com.takibo.identitycore.interfaces.rest.response;

import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;

import java.time.Instant;
import java.util.UUID;

/**
 *  utilisé pour renvoyer les détails d'un utilisateur depuis la couche application.
 * Il représente une vue simplifiée de l'agrégat User.
 */
public record UserResponse(
        UUID id,
        UUID spaceId,
        String username,
        String email,
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
) {}