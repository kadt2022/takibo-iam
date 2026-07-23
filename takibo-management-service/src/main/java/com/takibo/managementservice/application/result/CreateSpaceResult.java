package com.takibo.managementservice.application.result;

import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.model.SpaceStatus;

import java.time.Instant;
import java.util.UUID;

public record CreateSpaceResult(
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
        long version
) {
    public static CreateSpaceResult from(Space space) {
        return new CreateSpaceResult(
                space.getId().value(),
                space.getOrgId(),
                space.getCode(),
                space.getName(),
                space.getDescription(),
                space.getStatus(),
                space.getStatusReason(),
                space.getStatusUpdatedAt(),
                space.getOwnerAccountId(),
                space.getCreatedAt(),
                space.getUpdatedAt(),
                space.getVersion()
        );
    }
}
