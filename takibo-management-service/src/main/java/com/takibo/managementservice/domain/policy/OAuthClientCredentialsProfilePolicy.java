package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class OAuthClientCredentialsProfilePolicy {

    public OAuthClientRegistration normalizeAndValidate(
            OAuthClientRegistration registration
    ) {
        assertClientCredentialsShape(registration);

        if (registration.grantTypes() == null
                || !registration.grantTypes().contains("client_credentials")) {
            return registration;
        }

        TokenEndpointAuthMethod normalizedAuthMethod =
                registration.tokenEndpointAuthMethod();
        if (normalizedAuthMethod == null
                || normalizedAuthMethod == TokenEndpointAuthMethod.none) {
            normalizedAuthMethod = TokenEndpointAuthMethod.client_secret_basic;
        }

        return new OAuthClientRegistration(
                registration.clientId(),
                registration.clientName(),
                ClientType.CONFIDENTIAL,
                true,
                normalizedAuthMethod,
                false,
                false,
                registration.jwksUri(),
                registration.jwksJson(),
                registration.idTokenSignedAlg(),
                registration.accessTokenTtlSeconds(),
                registration.refreshTokenTtlSeconds(),
                registration.idTokenTtlSeconds(),
                registration.clientSecretExpiresAt(),
                registration.scopes(),
                registration.grantTypes(),
                Set.of(),
                Set.of(),
                Set.of()
        );
    }

    private static void assertClientCredentialsShape(
            OAuthClientRegistration registration
    ) {
        Set<String> normalizedGrantTypes =
                normalizeGrantTypesForPolicy(registration.grantTypes());
        if (!normalizedGrantTypes.contains("client_credentials")) {
            return;
        }

        if (normalizedGrantTypes.size() > 1) {
            throw new InvalidClientConfigurationException(
                    "client_credentials cannot be combined with other grant types"
            );
        }

        if (hasValues(registration.redirectUris())
                || hasValues(registration.postLogoutRedirectUris())
                || hasValues(registration.corsOrigins())) {
            throw new InvalidClientConfigurationException(
                    "client_credentials must not include redirect/cors/post-logout URIs"
            );
        }
    }

    private static Set<String> normalizeGrantTypesForPolicy(
            Set<String> grantTypes
    ) {
        if (grantTypes == null || grantTypes.isEmpty()) {
            return Set.of();
        }
        return grantTypes.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean hasValues(Set<String> values) {
        return values != null && !values.isEmpty();
    }
}
