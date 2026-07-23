package com.takibo.managementservice.domain.model;

import com.takibo.managementservice.domain.vo.SpaceId;

import java.util.Map;
import java.util.UUID;

public record OAuthClientRegistrationPlan(
        OAuthClientRegistration registration,
        TokenEndpointAuthMethod authMethod,
        boolean requirePkce,
        boolean requireSecret,
        ValidatedSets sets
) {

    public OAuthClient createClient(UUID orgId, SpaceId spaceId, Secrets secrets) {
        OAuthClient client = OAuthClient.create(
                        orgId,
                        spaceId,
                        registration.clientId(),
                        registration.clientName(),
                        registration.clientType()
                )
                .toBuilder()
                .tokenEndpointAuthMethod(authMethod)
                .requirePkce(requirePkce)
                .requireConsent(Boolean.TRUE.equals(registration.requireConsent()))
                .jwksUri(registration.jwksUri())
                .jwksJson(registration.jwksJson())
                .idTokenSignedAlg(registration.idTokenSignedAlg())
                .accessTokenTtlSeconds(registration.accessTokenTtlSeconds())
                .refreshTokenTtlSeconds(registration.refreshTokenTtlSeconds())
                .idTokenTtlSeconds(registration.idTokenTtlSeconds())
                .scopes(sets.scopes())
                .grantTypes(sets.grantTypes())
                .redirectUris(sets.redirectUris())
                .postLogoutRedirectUris(sets.postLogoutRedirectUris())
                .corsOrigins(sets.corsOrigins())
                .additionalSettings(Map.of())
                .build();

        if (secrets.hash() != null) {
            return client.withSecret(secrets.hash(), secrets.expiresAt());
        }
        return client;
    }
}
