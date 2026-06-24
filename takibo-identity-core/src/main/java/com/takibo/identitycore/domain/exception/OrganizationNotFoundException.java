package com.takibo.identitycore.domain.exception;

/**
 * Levée lorsqu'aucune organisation ne correspond au code fourni lors d'une résolution
 * de clé lisible. Exception de domaine pure : le mapping vers HTTP (404) est assuré
 * par la couche web ({@code interfaces/rest/advice}).
 */
public class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException(String message) {
        super(message);
    }

    public OrganizationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
