package com.takibo.identitycore.domain.exception;

import com.takibo.identitycore.domain.status.UserStatus;

/** Exception métier pour une transition de statut interdite. */
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }

    public InvalidStatusTransitionException(UserStatus from, UserStatus to) {
        super("Status transition not allowed: " + from + " -> " + to);
    }
}
