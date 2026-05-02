package com.takibo.identitycore.domain.rbac.model;

import com.takibo.identitycore.domain.model.Identity;

import java.time.Instant;
import java.util.UUID;

public record RoleAssignment(
        UUID id,
        UUID orgId,
        UUID spaceId,
        Identity identity,
        String roleCode,
        RoleSource roleSource,
        UUID businessRoleId,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {}
