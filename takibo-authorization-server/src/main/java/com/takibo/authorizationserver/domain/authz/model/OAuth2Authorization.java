package com.takibo.authorizationserver.domain.authz.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record OAuth2Authorization(
        UUID id,
        UUID orgId,
        UUID spaceId,
        String registeredClientId,
        UUID principalAccountId,

        String authorizationGrantType,
        String authorizedScopes,
        Map<String, Object> attributes,
        String state,

        HashedToken authorizationCode,
        StoredToken accessToken,
        StoredToken oidcIdToken,
        StoredToken refreshToken,
        HashedToken userCode,
        HashedToken deviceCode,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
