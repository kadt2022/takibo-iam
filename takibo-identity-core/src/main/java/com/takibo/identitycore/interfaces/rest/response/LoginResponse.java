package com.takibo.identitycore.interfaces.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Réponse de login. IAM 31 : en portée ORGANIZATION, {@code spaceId} et
 * {@code userId} sont absents de la réponse (pas null — absents), car le
 * user local est une réalité de space.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
