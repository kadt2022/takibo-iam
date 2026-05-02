package com.takibo.managementservice.interfaces.rest.request;

import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;

public record ClientRegistrationRequest(
        @NotBlank String clientId,
        @NotBlank String clientName,
        @NotNull ClientType clientType,
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
