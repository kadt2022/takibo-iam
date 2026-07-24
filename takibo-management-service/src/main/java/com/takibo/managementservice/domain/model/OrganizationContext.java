package com.takibo.managementservice.domain.model;

import java.util.UUID;

public record OrganizationContext(
        UUID orgId,
        boolean enabled,
        int currentSpaces
) {

    public boolean hasReachedSpaceLimit(int maximumSpaces) {
        return currentSpaces >= maximumSpaces;
    }
}
