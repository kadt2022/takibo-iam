package com.takibo.managementservice.domain.validation;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

public final class OAuthClientMetadataValidator {

    private static final Pattern CLIENT_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._~-]*");
    private static final Set<String> SAFE_ID_TOKEN_ALGORITHMS = Set.of(
            "RS256", "RS384", "RS512",
            "PS256", "PS384", "PS512",
            "ES256", "ES384", "ES512"
    );
    private static final int MAX_ACCESS_TOKEN_TTL_SECONDS = 86_400;
    private static final int MAX_REFRESH_TOKEN_TTL_SECONDS = 31_536_000;
    private static final int MAX_ID_TOKEN_TTL_SECONDS = 86_400;

    private final Clock clock;

    public OAuthClientMetadataValidator(Clock clock) {
        this.clock = clock;
    }

    public void validateRegistration(OAuthClientRegistration registration) {
        validateIdentity(registration);
        validateCollections(registration);
        validateTokenTtls(registration);
        validateSigningAlgorithm(registration.idTokenSignedAlg());
        validateSecretExpiration(registration.clientSecretExpiresAt());
        validateSecretConfiguration(registration);
    }

    public void validateSecretExpiration(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            fail("clientSecretExpiresAt must be in the future");
        }
    }

    private static void validateIdentity(
            OAuthClientRegistration registration
    ) {
        String clientId = registration.clientId();
        if (clientId.length() > 128
                || !CLIENT_ID_PATTERN.matcher(clientId).matches()) {
            fail("clientId must contain 1 to 128 URL-safe characters");
        }

        String clientName = registration.clientName();
        if (clientName.length() > 160
                || clientName.chars().anyMatch(Character::isISOControl)) {
            fail(
                    "clientName must contain at most 160 characters "
                            + "and no control characters"
            );
        }
    }

    private static void validateCollections(
            OAuthClientRegistration registration
    ) {
        if (registration.grantTypes() == null
                || registration.grantTypes().isEmpty()) {
            fail("at least one grant type is required");
        }
        validateSet("scopes", registration.scopes(), 50, 128);
        validateSet("grantTypes", registration.grantTypes(), 10, 64);
        validateSet("redirectUris", registration.redirectUris(), 20, 255);
        validateSet(
                "postLogoutRedirectUris",
                registration.postLogoutRedirectUris(),
                20,
                255
        );
        validateSet("corsOrigins", registration.corsOrigins(), 20, 255);
    }

    private static void validateSet(
            String name,
            Set<String> values,
            int maxItems,
            int maxLength
    ) {
        if (values == null) {
            return;
        }
        if (values.size() > maxItems) {
            fail(name + " exceeds the maximum number of values");
        }
        for (String value : values) {
            if (value == null
                    || value.isBlank()
                    || value.length() > maxLength) {
                fail(name + " contains an invalid value");
            }
        }
    }

    private static void validateTokenTtls(
            OAuthClientRegistration registration
    ) {
        validateTtl(
                "accessTokenTtlSeconds",
                registration.accessTokenTtlSeconds(),
                MAX_ACCESS_TOKEN_TTL_SECONDS
        );
        validateTtl(
                "refreshTokenTtlSeconds",
                registration.refreshTokenTtlSeconds(),
                MAX_REFRESH_TOKEN_TTL_SECONDS
        );
        validateTtl(
                "idTokenTtlSeconds",
                registration.idTokenTtlSeconds(),
                MAX_ID_TOKEN_TTL_SECONDS
        );
        if (registration.accessTokenTtlSeconds() != null
                && registration.refreshTokenTtlSeconds() != null
                && registration.refreshTokenTtlSeconds()
                <= registration.accessTokenTtlSeconds()) {
            fail(
                    "refreshTokenTtlSeconds must be greater than "
                            + "accessTokenTtlSeconds"
            );
        }
    }

    private static void validateTtl(
            String name,
            Integer value,
            int maximum
    ) {
        if (value != null && value <= 0) {
            fail(name + " must be positive");
        }
        if (value != null && value > maximum) {
            fail(
                    name + " exceeds the allowed maximum of "
                            + maximum + " seconds"
            );
        }
    }

    private static void validateSigningAlgorithm(String algorithm) {
        if (algorithm != null
                && !SAFE_ID_TOKEN_ALGORITHMS.contains(algorithm)) {
            fail("idTokenSignedAlg is not allowed");
        }
    }

    private static void validateSecretConfiguration(
            OAuthClientRegistration registration
    ) {
        if (registration.clientSecretExpiresAt() == null) {
            return;
        }
        TokenEndpointAuthMethod method =
                registration.tokenEndpointAuthMethod();
        if (registration.clientType() == ClientType.PUBLIC
                || method == TokenEndpointAuthMethod.none
                || method == TokenEndpointAuthMethod.private_key_jwt) {
            fail(
                    "clientSecretExpiresAt is only valid for clients "
                            + "using a secret"
            );
        }
    }

    private static void fail(String message) {
        throw new InvalidClientConfigurationException(message);
    }
}
