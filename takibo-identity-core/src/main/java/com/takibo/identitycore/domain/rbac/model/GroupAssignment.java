package com.takibo.identitycore.domain.rbac.model;

import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.infrastructure.entity.GroupRefKind;

import java.time.Instant;
import java.util.UUID;

public record GroupAssignment(
        UUID id,
        UUID orgId,
        UUID spaceId,
        UUID identityId,
        Identity identity,
        IdentityType identityType,
        String groupCode,
        GroupSource groupSource,
        UUID businessGroupId,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {}
