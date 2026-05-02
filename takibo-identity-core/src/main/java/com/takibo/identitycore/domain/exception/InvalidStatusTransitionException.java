package com.takibo.identitycore.domain.exception;

/** Exception métier pour une transition de statut interdite. */
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
