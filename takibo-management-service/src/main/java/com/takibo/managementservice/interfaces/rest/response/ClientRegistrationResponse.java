package com.takibo.managementservice.interfaces.rest.response;

import com.takibo.managementservice.domain.model.ClientType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ClientRegistrationResponse(
        UUID id,
        UUID orgId,
        UUID spaceId,
        String clientId,
        String clientName,
        ClientType clientType,
        boolean requireClientSecret,
        boolean requirePkce,
        Instant clientSecretExpiresAt,
        Set<String> scopes,
        Set<String> grantTypes,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris,
        Set<String> corsOrigins
) {}
