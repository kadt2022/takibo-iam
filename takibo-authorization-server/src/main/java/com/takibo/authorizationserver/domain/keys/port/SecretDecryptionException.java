package com.takibo.authorizationserver.domain.keys.port;

/**
 * Un secret n'a pas pu etre dechiffre : chiffre illisible, altere, ou produit avec une autre
 * cle. Volontairement sans detail dans le message — la cause exacte n'apprendrait rien a un
 * exploitant et renseignerait un attaquant.
 */
public class SecretDecryptionException extends RuntimeException {

    public SecretDecryptionException(String message) {
        super(message);
    }

    public SecretDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
