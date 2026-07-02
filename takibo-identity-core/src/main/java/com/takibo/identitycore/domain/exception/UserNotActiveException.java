package com.takibo.identitycore.domain.exception;

import com.takibo.identitycore.domain.status.UserStatus;

import java.util.UUID;

/**
 * Le mot de passe a été prouvé, mais le user local n'est pas {@code ACTIVE} dans le space :
 * l'état du user est une vraie frontière d'accès — aucune nouvelle preuve n'est émise.
 * <p>
 * Suspendre n'est pas décorer une fiche. Suspendre, c'est retirer la capacité de recevoir
 * une nouvelle preuve.
 */
public class UserNotActiveException extends RuntimeException {

    public UserNotActiveException(UUID userId, UserStatus status) {
        super("User " + userId + " is not active (status: " + status + ")");
    }
}
