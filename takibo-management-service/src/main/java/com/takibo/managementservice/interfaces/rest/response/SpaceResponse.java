package com.takibo.managementservice.interfaces.rest.response;

import com.takibo.managementservice.domain.model.SpaceOwnerType;
import com.takibo.managementservice.domain.model.SpaceStatus;

import java.time.Instant;
import java.util.UUID;
public record SpaceResponse(
        UUID id,
        UUID orgId,
        String code,
        String name,
        String description,
        SpaceStatus status,
        String statusReason,
        Instant statusUpdatedAt,
        UUID ownerAccountId,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {}
