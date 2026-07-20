package com.takibo.managementservice.application.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.managementservice.application.command.RegisterClientCommand;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.validation.UriValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class OAuthClientConfigurationValidator {

    private static final Pattern CLIENT_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._~-]*");
    private static final Set<String> SAFE_ID_TOKEN_ALGORITHMS = Set.of(
            "RS256", "RS384", "RS512",
            "PS256", "PS384", "PS512",
            "ES256", "ES384", "ES512",
            "EdDSA"
    );
    private static final Set<String> PUBLIC_JWK_TYPES = Set.of("RSA", "EC", "OKP");
    private static final Set<String> PRIVATE_JWK_MEMBERS = Set.of(
            "d", "p", "q", "dp", "dq", "qi", "oth", "k"
    );
    private static final int MAX_ACCESS_TOKEN_TTL_SECONDS = 86_400;
    private static final int MAX_REFRESH_TOKEN_TTL_SECONDS = 31_536_000;
    private static final int MAX_ID_TOKEN_TTL_SECONDS = 86_400;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public void validateRegistration(RegisterClientCommand command) {
        validateIdentity(command);
        validateCollections(command);
        validateTokenTtls(command);
        validateSigningAlgorithm(command.idTokenSignedAlg());
        validateSecretExpiration(command.clientSecretExpiresAt());
        validateSecretConfiguration(command);
        validateJwksConfiguration(command);
    }

    public void validateSecretExpiration(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            fail("clientSecretExpiresAt must be in the future");
        }
    }

    private static void validateIdentity(RegisterClientCommand command) {
        String clientId = command.clientId();
        if (clientId.length() > 128 || !CLIENT_ID_PATTERN.matcher(clientId).matches()) {
            fail("clientId must contain 1 to 128 URL-safe characters");
        }
        String clientName = command.clientName();
        if (clientName.length() > 160 || clientName.chars().anyMatch(Character::isISOControl)) {
            fail("clientName must contain at most 160 characters and no control characters");
        }
    }

    private static void validateCollections(RegisterClientCommand command) {
        if (command.grantTypes() == null || command.grantTypes().isEmpty()) {
            fail("at least one grant type is required");
        }
        validateSet("scopes", command.scopes(), 50, 128);
        validateSet("grantTypes", command.grantTypes(), 10, 64);
        validateSet("redirectUris", command.redirectUris(), 20, 255);
        validateSet("postLogoutRedirectUris", command.postLogoutRedirectUris(), 20, 255);
        validateSet("corsOrigins", command.corsOrigins(), 20, 255);
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

    private static void validateTokenTtls(RegisterClientCommand command) {
        validateTtl("accessTokenTtlSeconds", command.accessTokenTtlSeconds(), MAX_ACCESS_TOKEN_TTL_SECONDS);
        validateTtl("refreshTokenTtlSeconds", command.refreshTokenTtlSeconds(), MAX_REFRESH_TOKEN_TTL_SECONDS);
        validateTtl("idTokenTtlSeconds", command.idTokenTtlSeconds(), MAX_ID_TOKEN_TTL_SECONDS);
        if (command.accessTokenTtlSeconds() != null
                && command.refreshTokenTtlSeconds() != null
                && command.refreshTokenTtlSeconds() <= command.accessTokenTtlSeconds()) {
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

    private static void validateSecretConfiguration(RegisterClientCommand command) {
        Instant expiresAt = command.clientSecretExpiresAt();
        TokenEndpointAuthMethod method = command.tokenEndpointAuthMethod();
        if (expiresAt == null) {
            return;
        }
        if (command.clientType() == ClientType.PUBLIC
                || method == TokenEndpointAuthMethod.none
                || method == TokenEndpointAuthMethod.private_key_jwt) {
            fail("clientSecretExpiresAt is only valid for clients using a secret");
        }
    }

    private void validateJwksConfiguration(RegisterClientCommand command) {
        boolean hasUri = hasText(command.jwksUri());
        boolean hasJson = hasText(command.jwksJson());
        if (hasUri && hasJson) {
            fail("jwksUri and jwksJson are mutually exclusive");
        }
        if (command.tokenEndpointAuthMethod() == TokenEndpointAuthMethod.private_key_jwt
                && !hasUri && !hasJson) {
            fail("private_key_jwt requires jwksUri or jwksJson");
        }
        if (command.tokenEndpointAuthMethod() == TokenEndpointAuthMethod.private_key_jwt
                && Boolean.TRUE.equals(command.requireClientSecret())) {
            fail("private_key_jwt must not require a client secret");
        }
        if (hasUri) {
            validateJwksUri(command.jwksUri());
        }
        if (hasJson) {
            validateJwksJson(command.jwksJson());
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

    private void validateJwksJson(String rawJson) {
        if (rawJson.length() > 32768) {
            fail("jwksJson exceeds 32768 characters");
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode keys = root == null ? null : root.get("keys");
            if (keys == null || !keys.isArray() || keys.isEmpty() || keys.size() > 10) {
                fail("jwksJson must contain between 1 and 10 public keys");
            }
            for (JsonNode key : keys) {
                validatePublicJwk(key);
            }
        } catch (JsonProcessingException ex) {
            fail("jwksJson must be a valid JWK Set");
        }
    }

    private static void validatePublicJwk(JsonNode key) {
        String keyType = key != null && key.hasNonNull("kty") ? key.get("kty").asText() : null;
        if (!PUBLIC_JWK_TYPES.contains(keyType)) {
            fail("jwksJson contains an unsupported key type");
        }
        for (String member : PRIVATE_JWK_MEMBERS) {
            if (key.has(member)) {
                fail("jwksJson must contain public key material only");
            }
        }
        switch (keyType) {
            case "RSA" -> requireTextMembers(key, "n", "e");
            case "EC" -> requireTextMembers(key, "crv", "x", "y");
            case "OKP" -> requireTextMembers(key, "crv", "x");
            default -> fail("jwksJson contains an unsupported key type");
        }
    }

    private static void requireTextMembers(JsonNode key, String... memberNames) {
        for (String memberName : memberNames) {
            JsonNode member = key.get(memberName);
            if (member == null || !member.isTextual() || member.asText().isBlank()) {
                fail("jwksJson contains an incomplete public key");
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void fail(String message) {
        throw new InvalidClientConfigurationException(message);
    }
}
