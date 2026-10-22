package com.takibo.outbox.core.model;

import java.time.Instant;
import java.util.UUID;

public record OutboxEnvelope(
        UUID id,
        String eventType,
        String aggregateType,
        String aggregateId,
        UUID orgId,
        UUID spaceId,
        String payloadJson,
        String dedupKey,
        Instant createdAt
) {
    public OutboxEnvelope {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType is required");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId is required");
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("payloadJson is required");
        }
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static OutboxEnvelope of(
            String eventType,
            String aggregateType,
            String aggregateId,
            UUID orgId,
            UUID spaceId,
            String payloadJson,
            String dedupKey
    ) {
        return new OutboxEnvelope(
                UUID.randomUUID(),
                eventType,
                aggregateType,
                aggregateId,
                orgId,
                spaceId,
                payloadJson,
                dedupKey,
                Instant.now()
        );
    }
}
