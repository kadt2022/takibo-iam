package com.takibo.managementservice.application.command;

import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;

import java.time.Instant;
import java.util.Set;

public record
RegisterClientCommand(
        String clientId,
        String clientName,
        ClientType clientType,
        Boolean requireClientSecret,
        TokenEndpointAuthMethod tokenEndpointAuthMethod,
        Boolean requirePkce,
        Boolean requireConsent,
        String jwksUri,
        String jwksJson,
        String idTokenSignedAlg,
        Integer accessTokenTtlSeconds,
        Integer refreshTokenTtlSeconds,
        Integer idTokenTtlSeconds,
        Instant clientSecretExpiresAt,
        Set<String> scopes,
        Set<String> grantTypes,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris,
        Set<String> corsOrigins
) {}
