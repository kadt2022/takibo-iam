package com.takibo.authorizationserver.domain.authz.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OAuth2AuthorizationConsent(
        UUID id,
        UUID orgId,
        UUID spaceId,
        String registeredClientId,
        UUID principalAccountId,
        String authorities,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
