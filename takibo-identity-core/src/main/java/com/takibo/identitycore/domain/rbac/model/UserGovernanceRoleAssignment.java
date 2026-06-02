package com.takibo.identitycore.domain.rbac.model;

import java.time.Instant;
import java.util.UUID;

public record UserGovernanceRoleAssignment(
        UUID orgId,
        UUID spaceId,
        UUID userId,
        UUID governanceRoleId,
        Instant assignedAt
) {}
