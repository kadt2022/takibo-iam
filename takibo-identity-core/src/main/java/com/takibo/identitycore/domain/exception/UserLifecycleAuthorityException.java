package com.takibo.identitycore.domain.exception;

/**
 * SEC-HUNT-01 : un pouvoir délégué de cycle de vie ne restreint jamais le propriétaire, ni un
 * utilisateur dont l'autorité dépasse celle de son auteur (403).
 */
public class UserLifecycleAuthorityException extends RuntimeException {
    public UserLifecycleAuthorityException(String message) {
        super(message);
    }
}
