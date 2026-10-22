package com.takibo.outbox.core.model;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(
        UUID id,
        String eventType,
        String aggregateType,
        String aggregateId,
        UUID orgId,
        UUID spaceId,
        String payloadJson,
        OutboxStatus status,
        int attempts,
        Instant nextRunAt,
        String lastError,
        Instant lockedAt,
        String lockedBy,
        String dedupKey,
        Instant createdAt,
        Instant updatedAt
) {
}
