package com.takibo.identitycore.interfaces.rest.response;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String scopeLevel,
        UUID organizationId,
        UUID spaceId,
        UUID accountId,
        UUID userId
) {
}
