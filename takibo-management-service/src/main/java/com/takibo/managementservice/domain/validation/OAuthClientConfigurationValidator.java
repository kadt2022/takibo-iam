package com.takibo.managementservice.domain.validation;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.validation.UriValidation;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class OAuthClientConfigurationValidator {

    private static final Pattern CLIENT_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._~-]*");
    private static final Set<String> SAFE_ID_TOKEN_ALGORITHMS = Set.of(
            "RS256", "RS384", "RS512",
            "PS256", "PS384", "PS512",
            "ES256", "ES384", "ES512"
    );
    private static final Set<Curve> SUPPORTED_EC_CURVES = Set.of(Curve.P_256, Curve.P_384, Curve.P_521);
    private static final int MAX_ACCESS_TOKEN_TTL_SECONDS = 86_400;
    private static final int MAX_REFRESH_TOKEN_TTL_SECONDS = 31_536_000;
    private static final int MAX_ID_TOKEN_TTL_SECONDS = 86_400;

    private final Clock clock;

    public OAuthClientConfigurationValidator(Clock clock) {
        this.clock = clock;
    }

    public void validateRegistration(OAuthClientRegistration registration) {
        validateIdentity(registration);
        validateCollections(registration);
        validateTokenTtls(registration);
        validateSigningAlgorithm(registration.idTokenSignedAlg());
        validateSecretExpiration(registration.clientSecretExpiresAt());
        validateSecretConfiguration(registration);
        validateJwksConfiguration(registration);
    }

    public void validateSecretExpiration(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            fail("clientSecretExpiresAt must be in the future");
        }
    }

    private static void validateIdentity(OAuthClientRegistration registration) {
        String clientId = registration.clientId();
        if (clientId.length() > 128 || !CLIENT_ID_PATTERN.matcher(clientId).matches()) {
            fail("clientId must contain 1 to 128 URL-safe characters");
        }
        String clientName = registration.clientName();
        if (clientName.length() > 160 || clientName.chars().anyMatch(Character::isISOControl)) {
            fail("clientName must contain at most 160 characters and no control characters");
        }
    }

    private static void validateCollections(OAuthClientRegistration registration) {
        if (registration.grantTypes() == null || registration.grantTypes().isEmpty()) {
            fail("at least one grant type is required");
        }
        validateSet("scopes", registration.scopes(), 50, 128);
        validateSet("grantTypes", registration.grantTypes(), 10, 64);
        validateSet("redirectUris", registration.redirectUris(), 20, 255);
        validateSet("postLogoutRedirectUris", registration.postLogoutRedirectUris(), 20, 255);
        validateSet("corsOrigins", registration.corsOrigins(), 20, 255);
    }

    private static void validateSet(String name, Set<String> values, int maxItems, int maxLength) {
        if (values == null) {
            return;
        }
        if (values.size() > maxItems) {
            fail(name + " exceeds the maximum number of values");
        }
        for (String value : values) {
            if (value == null || value.isBlank() || value.length() > maxLength) {
                fail(name + " contains an invalid value");
            }
        }
    }

    private static void validateTokenTtls(OAuthClientRegistration registration) {
        validateTtl("accessTokenTtlSeconds", registration.accessTokenTtlSeconds(), MAX_ACCESS_TOKEN_TTL_SECONDS);
        validateTtl("refreshTokenTtlSeconds", registration.refreshTokenTtlSeconds(), MAX_REFRESH_TOKEN_TTL_SECONDS);
        validateTtl("idTokenTtlSeconds", registration.idTokenTtlSeconds(), MAX_ID_TOKEN_TTL_SECONDS);
        if (registration.accessTokenTtlSeconds() != null
                && registration.refreshTokenTtlSeconds() != null
                && registration.refreshTokenTtlSeconds() <= registration.accessTokenTtlSeconds()) {
            fail("refreshTokenTtlSeconds must be greater than accessTokenTtlSeconds");
        }
    }

    private static void validateTtl(String name, Integer value, int maximum) {
        if (value != null && value <= 0) {
            fail(name + " must be positive");
        }
        if (value != null && value > maximum) {
            fail(name + " exceeds the allowed maximum of " + maximum + " seconds");
        }
    }

    private static void validateSigningAlgorithm(String algorithm) {
        if (algorithm != null && !SAFE_ID_TOKEN_ALGORITHMS.contains(algorithm)) {
            fail("idTokenSignedAlg is not allowed");
        }
    }

    private static void validateSecretConfiguration(OAuthClientRegistration registration) {
        Instant expiresAt = registration.clientSecretExpiresAt();
        TokenEndpointAuthMethod method = registration.tokenEndpointAuthMethod();
        if (expiresAt == null) {
            return;
        }
        if (registration.clientType() == ClientType.PUBLIC
                || method == TokenEndpointAuthMethod.none
                || method == TokenEndpointAuthMethod.private_key_jwt) {
            fail("clientSecretExpiresAt is only valid for clients using a secret");
        }
    }

    private void validateJwksConfiguration(OAuthClientRegistration registration) {
        boolean hasUri = hasText(registration.jwksUri());
        boolean hasJson = hasText(registration.jwksJson());
        if (hasUri && hasJson) {
            fail("jwksUri and jwksJson are mutually exclusive");
        }
        if (registration.tokenEndpointAuthMethod() == TokenEndpointAuthMethod.private_key_jwt
                && !hasUri && !hasJson) {
            fail("private_key_jwt requires jwksUri or jwksJson");
        }
        if (registration.tokenEndpointAuthMethod() == TokenEndpointAuthMethod.private_key_jwt
                && !hasText(registration.idTokenSignedAlg())) {
            fail("private_key_jwt requires idTokenSignedAlg");
        }
        if (registration.tokenEndpointAuthMethod() == TokenEndpointAuthMethod.private_key_jwt
                && Boolean.TRUE.equals(registration.requireClientSecret())) {
            fail("private_key_jwt must not require a client secret");
        }
        if (hasUri) {
            validateJwksUri(registration.jwksUri());
        }
        if (hasJson) {
            validateJwksJson(registration.jwksJson(), registration.idTokenSignedAlg());
        }
    }

    private static void validateJwksUri(String rawUri) {
        if (rawUri.length() > 255) {
            fail("jwksUri exceeds 255 characters");
        }
        try {
            UriValidation.requireHttpsEndpoint(rawUri);
        } catch (RuntimeException ex) {
            fail("jwksUri must be an absolute HTTPS URL without user-info or fragment");
        }
    }

    private static void validateJwksJson(String rawJson, String configuredAlgorithm) {
        if (rawJson.length() > 32768) {
            fail("jwksJson exceeds 32768 characters");
        }
        try {
            List<JWK> keys = JWKSet.parse(rawJson).getKeys();
            if (keys.isEmpty() || keys.size() > 10) {
                fail("jwksJson must contain between 1 and 10 public keys");
            }
            boolean compatibleKeyFound = false;
            for (JWK key : keys) {
                validatePublicJwk(key);
                compatibleKeyFound |= isCompatibleWithAlgorithm(key, configuredAlgorithm);
            }
            if (hasText(configuredAlgorithm) && !compatibleKeyFound) {
                fail("jwksJson does not contain a key compatible with idTokenSignedAlg");
            }
        } catch (ParseException | JOSEException | IllegalArgumentException ex) {
            fail("jwksJson must be a valid JWK Set");
        }
    }

    private static void validatePublicJwk(JWK key) throws JOSEException {
        if (!(key instanceof RSAKey) && !(key instanceof ECKey)) {
            fail("jwksJson contains an unsupported key type");
        }
        if (key.isPrivate()) {
            fail("jwksJson must contain public key material only");
        }
        if (key.getKeyUse() != null && !KeyUse.SIGNATURE.equals(key.getKeyUse())) {
            fail("jwksJson keys must be usable for signatures");
        }
        if (key.getKeyOperations() != null && !key.getKeyOperations().isEmpty()
                && !key.getKeyOperations().contains(KeyOperation.VERIFY)) {
            fail("jwksJson keys must allow signature verification");
        }
        if (key instanceof RSAKey rsaKey) {
            rsaKey.toRSAPublicKey();
            if (rsaKey.size() < 2048) {
                fail("jwksJson RSA keys must be at least 2048 bits");
            }
            return;
        }
        if (key instanceof ECKey ecKey) {
            if (!SUPPORTED_EC_CURVES.contains(ecKey.getCurve())) {
                fail("jwksJson contains an unsupported EC curve");
            }
            ecKey.toECPublicKey();
            return;
        }
        fail("jwksJson contains an unsupported key type");
    }

    private static boolean isCompatibleWithAlgorithm(JWK key, String configuredAlgorithm) {
        if (!hasText(configuredAlgorithm)) {
            return true;
        }
        if (key.getAlgorithm() != null && !configuredAlgorithm.equals(key.getAlgorithm().getName())) {
            return false;
        }
        return switch (configuredAlgorithm) {
            case "RS256", "RS384", "RS512", "PS256", "PS384", "PS512" -> key instanceof RSAKey;
            case "ES256" -> key instanceof ECKey ecKey && Curve.P_256.equals(ecKey.getCurve());
            case "ES384" -> key instanceof ECKey ecKey && Curve.P_384.equals(ecKey.getCurve());
            case "ES512" -> key instanceof ECKey ecKey && Curve.P_521.equals(ecKey.getCurve());
            default -> false;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void fail(String message) {
        throw new InvalidClientConfigurationException(message);
    }
}
