package com.takibo.authorizationserver.domain.keys.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContextHmacKey(
        UUID keyId,
        UUID orgId,
        UUID spaceId,

        int keyVersion,
        String keyValue,
        KeyStatus status,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime retiredAt,
        OffsetDateTime revokedAt,
        String revokeReason
) {
}
