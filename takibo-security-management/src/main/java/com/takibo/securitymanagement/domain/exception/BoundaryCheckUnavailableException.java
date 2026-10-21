package com.takibo.securitymanagement.domain.exception;

/**
 * Le contrôle d'appartenance n'a pas pu être évalué parce que la base est temporairement
 * indisponible. Ce n'est ni un refus ni une absence : la requête est arrêtée (503 générique).
 */
public class BoundaryCheckUnavailableException extends RuntimeException {

    public BoundaryCheckUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
