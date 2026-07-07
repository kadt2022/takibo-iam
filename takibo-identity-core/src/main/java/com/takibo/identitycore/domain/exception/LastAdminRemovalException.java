package com.takibo.identitycore.domain.exception;

/**
 * Un space ne perd jamais son dernier chemin admin : retirer le dernier
 * R_SPACE_ADMIN direct ou le dernier membership G_SPACE_ADMINS est un conflit
 * avec l'état de gouvernance du space (409).
 */
public class LastAdminRemovalException extends RuntimeException {
    public LastAdminRemovalException(String message) {
        super(message);
    }
}
