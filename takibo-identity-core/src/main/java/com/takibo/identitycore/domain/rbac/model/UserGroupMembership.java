package com.takibo.identitycore.domain.rbac.model;

import java.time.Instant;
import java.util.UUID;

public record UserGroupMembership(
        UUID organizationId,
        UUID spaceId,
        UUID userId,
        UUID groupId,
        Instant assignedAt,
        String assignedBy
) {
}
