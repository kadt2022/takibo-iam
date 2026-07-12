package com.takibo.identitycore.domain.exception;

/**
 * Échec de login, toutes causes confondues (IAM 31).
 * <p>
 * La surface d'authentification ne raconte rien : organisation inexistante,
 * compte inconnu, mauvais mot de passe, compte verrouillé, space inaccessible —
 * une seule réponse externe. La cause réelle vit dans l'audit serveur.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Impossible de valider cette connexion.");
    }
}
