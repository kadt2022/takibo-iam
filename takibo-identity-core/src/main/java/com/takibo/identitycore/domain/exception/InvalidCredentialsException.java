package com.takibo.identitycore.domain.exception;

/**
 * Échec d'authentification humaine — volontairement indistinct.
 * <p>
 * Levée pour : email inconnu dans l'org, credentials absents, mot de passe invalide,
 * account rattaché à une autre org. Le message ne doit jamais révéler laquelle de ces
 * causes s'est produite (anti-énumération).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
