package com.takibo.identitycore.domain.exception;

import java.util.UUID;

/**
 * L'account est authentifié mais ne possède aucun user local dans le space demandé.
 * Aucun token SPACE ne peut être émis sans user situé.
 */
public class UserNotMemberOfSpaceException extends RuntimeException {

    public UserNotMemberOfSpaceException(UUID spaceId) {
        super("No local user in space " + spaceId);
    }
}
