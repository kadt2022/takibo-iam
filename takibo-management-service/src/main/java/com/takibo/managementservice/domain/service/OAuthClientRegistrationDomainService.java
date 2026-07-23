package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.exception.AuthorizationCodeRequiresRedirectUriException;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.exception.PublicAuthorizationCodeRequiresPkceException;
import com.takibo.managementservice.domain.exception.PublicClientAuthMethodNotNoneException;
import com.takibo.managementservice.domain.exception.PublicClientMustNotHaveSecretException;
import com.takibo.managementservice.domain.model.ClientCorsOrigin;
import com.takibo.managementservice.domain.model.ClientGrantType;
import com.takibo.managementservice.domain.model.ClientPostLogoutRedirectUri;
import com.takibo.managementservice.domain.model.ClientRedirectUri;
import com.takibo.managementservice.domain.model.ClientScope;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.OAuthClientRegistrationPlan;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.model.ValidatedSets;
import com.takibo.managementservice.domain.validation.OAuthClientConfigurationValidator;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public final class OAuthClientRegistrationDomainService {

    private final OAuthClientConfigurationValidator configurationValidator;

    public OAuthClientRegistrationDomainService(
            OAuthClientConfigurationValidator configurationValidator
    ) {
        this.configurationValidator = configurationValidator;
    }

    public OAuthClientRegistrationPlan prepareRegistration(OAuthClientRegistration registration) {
        hardFailClientCredentialsPolicy(registration);

        OAuthClientRegistration normalized = normalizeForClientCredentials(registration);
        configurationValidator.validateRegistration(normalized);

        TokenEndpointAuthMethod authMethod =
                resolveAuthMethod(normalized, normalized.clientType());
        enforcePublicBasics(
                normalized.clientType(),
                authMethod,
                normalized.requireClientSecret()
        );
        enforceConfidentialBasics(
                normalized.clientType(),
                authMethod,
                normalized.requireClientSecret()
        );

        ValidatedSets sets = validateAndNormalizeSets(normalized);
        boolean requirePkce = resolvePkce(
                normalized,
                normalized.clientType(),
                sets.grantTypes().contains("authorization_code")
        );
        enforceAuthorizationCodeRules(normalized.clientType(), sets, requirePkce);
        enforceClientCredentialsRules(normalized, authMethod, sets);
        enforcePublicSpaRules(normalized, authMethod, sets, requirePkce);
        enforceConfidentialAuthCodeRules(normalized, authMethod, sets);

        boolean requireSecret = resolveRequireSecret(
                normalized,
                normalized.clientType(),
                authMethod
        );

        return new OAuthClientRegistrationPlan(
                normalized,
                authMethod,
                requirePkce,
                requireSecret,
                sets
        );
    }

    public void validateSecretExpiration(Instant expiresAt) {
        configurationValidator.validateSecretExpiration(expiresAt);
    }

    public Instant resolveSecretRotationExpiration(
            OAuthClient client,
            Instant requestedExpiration
    ) {
        if (client.getClientType() == ClientType.PUBLIC
                || !usesSecret(client.getTokenEndpointAuthMethod())) {
            throw new InvalidClientConfigurationException("client does not use secrets");
        }

        Instant resolved = requestedExpiration != null
                ? requestedExpiration
                : client.getClientSecretExpiresAt();
        configurationValidator.validateSecretExpiration(resolved);
        return resolved;
    }

    private static void hardFailClientCredentialsPolicy(OAuthClientRegistration registration) {
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

    private static Set<String> normalizeGrantTypesForPolicy(Set<String> grantTypes) {
        if (grantTypes == null || grantTypes.isEmpty()) {
            return Set.of();
        }
        return grantTypes.stream()
                .filter(value -> value != null)
                .map(value -> value.trim().toLowerCase())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean hasValues(Set<String> values) {
        return values != null && !values.isEmpty();
    }

    private static TokenEndpointAuthMethod resolveAuthMethod(
            OAuthClientRegistration registration,
            ClientType type
    ) {
        TokenEndpointAuthMethod explicit = registration.tokenEndpointAuthMethod();
        if (explicit != null) {
            return explicit;
        }
        if (type == ClientType.PUBLIC) {
            return TokenEndpointAuthMethod.none;
        }
        return TokenEndpointAuthMethod.client_secret_basic;
    }

    private static void enforcePublicBasics(
            ClientType type,
            TokenEndpointAuthMethod method,
            Boolean requireClientSecret
    ) {
        if (type == ClientType.PUBLIC && method != TokenEndpointAuthMethod.none) {
            throw new PublicClientAuthMethodNotNoneException(method.name());
        }
        if (type == ClientType.PUBLIC && Boolean.TRUE.equals(requireClientSecret)) {
            throw new PublicClientMustNotHaveSecretException();
        }
    }

    private static void enforceConfidentialBasics(
            ClientType type,
            TokenEndpointAuthMethod method,
            Boolean requireClientSecret
    ) {
        if (type != ClientType.CONFIDENTIAL) {
            return;
        }
        if (method == TokenEndpointAuthMethod.none) {
            throw new InvalidClientConfigurationException(
                    "Confidential clients require clientSecret "
                            + "(token_endpoint_auth_method cannot be none)"
            );
        }
        if (method != TokenEndpointAuthMethod.private_key_jwt
                && Boolean.FALSE.equals(requireClientSecret)) {
            throw new InvalidClientConfigurationException(
                    "Confidential clients require clientSecret "
                            + "(requireClientSecret=true)"
            );
        }
    }

    private static void enforceClientCredentialsRules(
            OAuthClientRegistration registration,
            TokenEndpointAuthMethod authMethod,
            ValidatedSets sets
    ) {
        if (!sets.grantTypes().contains("client_credentials")) {
            return;
        }
        if (sets.grantTypes().size() != 1) {
            throw new InvalidClientConfigurationException(
                    "client_credentials cannot be combined with other grant types"
            );
        }
        if (registration.clientType() != ClientType.CONFIDENTIAL) {
            throw new InvalidClientConfigurationException(
                    "client_credentials requires clientType=CONFIDENTIAL"
            );
        }
        if (authMethod == TokenEndpointAuthMethod.none) {
            throw new InvalidClientConfigurationException(
                    "client_credentials requires client authentication"
            );
        }
        if (Boolean.TRUE.equals(registration.requirePkce())) {
            throw new InvalidClientConfigurationException(
                    "client_credentials must not use PKCE"
            );
        }
        if (!sets.redirectUris().isEmpty()
                || !sets.postLogoutRedirectUris().isEmpty()
                || !sets.corsOrigins().isEmpty()) {
            throw new InvalidClientConfigurationException(
                    "client_credentials must not include redirect/cors/post-logout URIs"
            );
        }
    }

    private static void enforcePublicSpaRules(
            OAuthClientRegistration registration,
            TokenEndpointAuthMethod authMethod,
            ValidatedSets sets,
            boolean requirePkce
    ) {
        if (registration.clientType() != ClientType.PUBLIC) {
            return;
        }
        if (authMethod != TokenEndpointAuthMethod.none) {
            throw new InvalidClientConfigurationException(
                    "PUBLIC clients must use token_endpoint_auth_method=none"
            );
        }
        if (Boolean.TRUE.equals(registration.requireClientSecret())) {
            throw new InvalidClientConfigurationException(
                    "PUBLIC clients must not require clientSecret"
            );
        }
        if (sets.grantTypes().contains("authorization_code")) {
            if (!requirePkce) {
                throw new InvalidClientConfigurationException(
                        "PUBLIC authorization_code requires PKCE"
                );
            }
            if (sets.redirectUris().isEmpty()) {
                throw new InvalidClientConfigurationException(
                        "authorization_code requires redirectUris"
                );
            }
            if (sets.corsOrigins().isEmpty()) {
                throw new InvalidClientConfigurationException(
                        "PUBLIC clients should declare corsOrigins"
                );
            }
        }
    }

    private static void enforceConfidentialAuthCodeRules(
            OAuthClientRegistration registration,
            TokenEndpointAuthMethod authMethod,
            ValidatedSets sets
    ) {
        if (registration.clientType() != ClientType.CONFIDENTIAL
                || !sets.grantTypes().contains("authorization_code")) {
            return;
        }
        if (authMethod == TokenEndpointAuthMethod.none) {
            throw new InvalidClientConfigurationException(
                    "CONFIDENTIAL authorization_code requires client authentication"
            );
        }
        if (authMethod != TokenEndpointAuthMethod.private_key_jwt
                && Boolean.FALSE.equals(registration.requireClientSecret())) {
            throw new InvalidClientConfigurationException(
                    "CONFIDENTIAL clients require clientSecret"
            );
        }
        if (sets.redirectUris().isEmpty()) {
            throw new InvalidClientConfigurationException(
                    "authorization_code requires redirectUris"
            );
        }
    }

    private static ValidatedSets validateAndNormalizeSets(
            OAuthClientRegistration registration
    ) {
        Set<String> grantTypes = ClientGrantType.ofAll(registration.grantTypes())
                .stream()
                .map(ClientGrantType::getValue)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> scopes = ClientScope.ofAll(registration.scopes())
                .stream()
                .map(ClientScope::getValue)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> redirectUris = ClientRedirectUri.ofAll(registration.redirectUris())
                .stream()
                .map(ClientRedirectUri::getUri)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> postLogoutRedirectUris =
                ClientPostLogoutRedirectUri.ofAll(registration.postLogoutRedirectUris())
                        .stream()
                        .map(ClientPostLogoutRedirectUri::getUri)
                        .collect(Collectors.toUnmodifiableSet());

        Set<String> corsOrigins = ClientCorsOrigin.ofAll(registration.corsOrigins())
                .stream()
                .map(ClientCorsOrigin::getOrigin)
                .collect(Collectors.toUnmodifiableSet());

        return new ValidatedSets(
                grantTypes,
                scopes,
                redirectUris,
                postLogoutRedirectUris,
                corsOrigins
        );
    }

    private static boolean resolvePkce(
            OAuthClientRegistration registration,
            ClientType type,
            boolean hasAuthorizationCode
    ) {
        Boolean explicit = registration.requirePkce();
        if (explicit != null) {
            return explicit;
        }
        return type == ClientType.PUBLIC && hasAuthorizationCode;
    }

    private static void enforceAuthorizationCodeRules(
            ClientType type,
            ValidatedSets sets,
            boolean requirePkce
    ) {
        boolean hasAuthorizationCode =
                sets.grantTypes().contains("authorization_code");
        if (!hasAuthorizationCode) {
            return;
        }

        if (sets.redirectUris().isEmpty()) {
            throw new AuthorizationCodeRequiresRedirectUriException();
        }
        if (type == ClientType.PUBLIC && !requirePkce) {
            throw new PublicAuthorizationCodeRequiresPkceException();
        }
    }

    private static boolean resolveRequireSecret(
            OAuthClientRegistration registration,
            ClientType type,
            TokenEndpointAuthMethod method
    ) {
        if (type == ClientType.PUBLIC) {
            return false;
        }
        boolean requested = Boolean.TRUE.equals(
                registration.requireClientSecret()
        );
        return requested || usesSecret(method);
    }

    private static boolean usesSecret(TokenEndpointAuthMethod method) {
        return method == TokenEndpointAuthMethod.client_secret_basic
                || method == TokenEndpointAuthMethod.client_secret_post
                || method == TokenEndpointAuthMethod.client_secret_jwt;
    }

    private static OAuthClientRegistration normalizeForClientCredentials(
            OAuthClientRegistration registration
    ) {
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
}
