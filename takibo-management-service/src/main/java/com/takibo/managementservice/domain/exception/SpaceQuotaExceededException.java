package com.takibo.managementservice.domain.exception;

import java.util.UUID;

public class SpaceQuotaExceededException extends RuntimeException {
    public SpaceQuotaExceededException(UUID orgId, int maxSpaces, int currentSpaces) {
        super("Space quota exceeded for orgId=" + orgId + " max=" + maxSpaces + " current=" + currentSpaces);
    }
}
