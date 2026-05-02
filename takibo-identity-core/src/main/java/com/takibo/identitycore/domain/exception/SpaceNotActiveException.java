package com.takibo.identitycore.domain.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class SpaceNotActiveException extends RuntimeException {
    private final UUID spaceId;
    public SpaceNotActiveException(UUID spaceId) {
        super("Space " + spaceId + " is not ACTIVE");
        this.spaceId = spaceId;
    }
}
