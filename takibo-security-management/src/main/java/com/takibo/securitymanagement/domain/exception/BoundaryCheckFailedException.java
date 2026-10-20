package com.takibo.securitymanagement.domain.exception;

/**
 * Le contrôle d'appartenance n'a pas pu être évalué pour une raison structurelle (SQL, mapping,
 * donnée incohérente). La requête est arrêtée (500 générique) ; la cause reste côté serveur.
 */
public class BoundaryCheckFailedException extends RuntimeException {

    public BoundaryCheckFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
