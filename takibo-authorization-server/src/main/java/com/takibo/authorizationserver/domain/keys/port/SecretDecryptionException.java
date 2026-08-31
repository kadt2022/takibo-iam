package com.takibo.authorizationserver.domain.keys.port;

/**
 * Un secret n'a pas pu etre dechiffre : chiffre illisible, altere, produit avec une autre cle,
 * ou incoherent avec le hash stocke a cote de lui. Volontairement sans detail dans le message —
 * la cause exacte n'apprendrait rien a un exploitant et renseignerait un attaquant.
 * <p>
 * <b>Tous</b> les chemins d'echec doivent passer par {@link #opaque()} / {@link #opaque(Throwable)}
 * et donc par le meme message {@link #FAILED}. Un message specifique par cause — meme
 * apparemment anodin comme « le hash ne correspond pas » plutot que « le dechiffrement a
 * echoue » — redonnerait a un attaquant exactement l'oracle que l'opacite cherche a fermer :
 * savoir laquelle des deux colonnes il a reussi a alterer.
 */
public class SecretDecryptionException extends RuntimeException {

    /** Seul message expose, quelle que soit la cause reelle. */
    public static final String FAILED = "SECRET_DECRYPTION_FAILED";

    public static SecretDecryptionException opaque() {
        return new SecretDecryptionException(FAILED);
    }

    public static SecretDecryptionException opaque(Throwable cause) {
        return new SecretDecryptionException(FAILED, cause);
    }

    protected SecretDecryptionException(String message) {
        super(message);
    }

    protected SecretDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
