package com.takibo.authorizationserver.domain.security.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountSecurityState(
        UUID orgId,
        UUID accountId,
        int currentEpoch,
        String lastBumpReason,
        OffsetDateTime lastBumpAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
