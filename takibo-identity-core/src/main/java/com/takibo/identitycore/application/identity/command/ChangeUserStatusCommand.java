package com.takibo.identitycore.application.identity.command;

import com.takibo.identitycore.domain.status.UserStatus;

import java.util.UUID;

/**
 * Changement de statut d'un user. Le {@code reason} est une justification
 * d'action administrative (audit/logs uniquement) — pas un attribut durable du user.
 */
public record ChangeUserStatusCommand(
        UUID userId,
        UserStatus targetStatus,
        String reason
) {
}
