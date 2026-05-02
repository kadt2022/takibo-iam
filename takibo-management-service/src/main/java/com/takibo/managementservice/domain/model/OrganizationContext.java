package com.takibo.managementservice.domain.model;

import java.util.UUID;

public record OrganizationContext(
    UUID orgId,
    boolean enabled,
   // int maxSpaces,
    int currentSpaces
) {
    public boolean quotaExceeded() {
        return currentSpaces >=10;
    }
}
