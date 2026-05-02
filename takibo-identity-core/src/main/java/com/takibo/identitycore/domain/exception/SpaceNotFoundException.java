package com.takibo.identitycore.domain.exception;

import java.util.UUID;

public class SpaceNotFoundException extends RuntimeException {
    private UUID spaceId;

    public SpaceNotFoundException(String message) {
        super(message);
    }

    public SpaceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public SpaceNotFoundException(UUID spaceId) {
        super("Space " + spaceId + " not found");
        this.spaceId = spaceId;
    }
}
