package com.takibo.managementservice.domain.model;

import com.takibo.managementservice.domain.vo.SpaceId;

import java.time.Instant;
import java.util.UUID;

public record SpaceCreationRequest(
        OrganizationContext organization,
        UUID ownerAccountId,
        String code,
        String name,
        String description,
        SpaceId spaceId,
        Instant createdAt
) {
}
