package com.takibo.identitycore.domain.exception;

import com.takibo.identitycore.domain.status.SpaceGuardStatus;

import java.util.UUID;

public class SpaceGuardException extends RuntimeException {

    private final SpaceGuardStatus code;
    private final UUID spaceId;

    public SpaceGuardException(SpaceGuardStatus code, UUID spaceId, String message) {
        super(message);
        this.code = code;
        this.spaceId = spaceId;
    }

    public SpaceGuardStatus getCode() { return code; }
    public UUID getSpaceId() { return spaceId; }
}
