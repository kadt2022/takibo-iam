package com.takibo.authorizationserver.infrastructure.keys;

/**
 * Aucune cle exploitable pour signer ou verifier.
 * <p>
 * Volontairement non recuperable : TAS sans cle ne peut ni emettre ni valider, et poursuivre
 * dans cet etat produirait des refus incomprehensibles a chaque requete plutot qu'un echec
 * net. Le message nomme la cause structurelle — jamais le contenu d'une cle.
 */
public class SigningKeyUnavailableException extends IllegalStateException {

    public SigningKeyUnavailableException(String message) {
        super(message);
    }

    public SigningKeyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
