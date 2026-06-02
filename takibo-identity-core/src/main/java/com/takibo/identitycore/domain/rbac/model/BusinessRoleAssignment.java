package com.takibo.identitycore.domain.rbac.model;

import java.time.Instant;
import java.util.UUID;

public record BusinessRoleAssignment(
        UUID orgId,
        UUID spaceId,
        UUID identityId,
        UUID businessRoleId,
        Instant createdAt
) {}
