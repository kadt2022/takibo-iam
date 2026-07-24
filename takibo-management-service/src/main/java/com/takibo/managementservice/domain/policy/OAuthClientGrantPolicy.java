package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.exception.AuthorizationCodeRequiresRedirectUriException;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.exception.PublicAuthorizationCodeRequiresPkceException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.model.ValidatedSets;

public final class OAuthClientGrantPolicy {

    public boolean resolvePkce(
            OAuthClientRegistration registration,
            ValidatedSets sets
    ) {
        if (registration.requirePkce() != null) {
            return registration.requirePkce();
        }
        return registration.clientType() == ClientType.PUBLIC
                && sets.grantTypes().contains("authorization_code");
    }

    public void validateGrantProfile(
            OAuthClientRegistration registration,
            TokenEndpointAuthMethod authMethod,
            ValidatedSets sets,
            boolean requirePkce
    ) {
        validateAuthorizationCode(
                registration.clientType(),
                sets,
                requirePkce
        );
        validateClientCredentials(registration, authMethod, sets);
        validatePublicSpa(
                registration,
                authMethod,
                sets,
                requirePkce
        );
        validateConfidentialAuthorizationCode(
                registration,
                authMethod,
                sets
        );
    }

    private static void validateAuthorizationCode(
            ClientType type,
            ValidatedSets sets,
            boolean requirePkce
    ) {
        if (!sets.grantTypes().contains("authorization_code")) {
            return;
        }
        if (sets.redirectUris().isEmpty()) {
            throw new AuthorizationCodeRequiresRedirectUriException();
        }
        if (type == ClientType.PUBLIC && !requirePkce) {
            throw new PublicAuthorizationCodeRequiresPkceException();
        }
    }

    private static void validateClientCredentials(
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

    private static void validatePublicSpa(
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
        if (!sets.grantTypes().contains("authorization_code")) {
            return;
        }
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

    private static void validateConfidentialAuthorizationCode(
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
                && Boolean.FALSE.equals(
                        registration.requireClientSecret()
                )) {
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
}
