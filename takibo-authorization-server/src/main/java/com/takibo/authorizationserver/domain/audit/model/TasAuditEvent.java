package com.takibo.authorizationserver.domain.audit.model;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record TasAuditEvent(
        UUID eventId,
        UUID orgId,
        UUID spaceId,

        String eventType,
        String status,

        UUID accountId,
        String clientId,

        OffsetDateTime occurredAt,

        InetAddress ipAddress,
        String userAgent,

        Map<String, Object> metadataJson
) {
    public TasAuditEvent {
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt cannot be null");
        }
    }
}
