package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.exception.PublicClientAuthMethodNotNoneException;
import com.takibo.managementservice.domain.exception.PublicClientMustNotHaveSecretException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;

public final class OAuthClientAuthenticationPolicy {

    public TokenEndpointAuthMethod resolveAuthMethod(
            OAuthClientRegistration registration
    ) {
        TokenEndpointAuthMethod explicit =
                registration.tokenEndpointAuthMethod();
        if (explicit != null) {
            return explicit;
        }
        if (registration.clientType() == ClientType.PUBLIC) {
            return TokenEndpointAuthMethod.none;
        }
        return TokenEndpointAuthMethod.client_secret_basic;
    }

    public void validateAuthenticationProfile(
            OAuthClientRegistration registration,
            TokenEndpointAuthMethod method
    ) {
        validatePublicClient(registration, method);
        validateConfidentialClient(registration, method);
    }

    public boolean requiresSecret(
            OAuthClientRegistration registration,
            TokenEndpointAuthMethod method
    ) {
        if (registration.clientType() == ClientType.PUBLIC) {
            return false;
        }
        return Boolean.TRUE.equals(registration.requireClientSecret())
                || usesSecret(method);
    }

    public boolean usesSecret(TokenEndpointAuthMethod method) {
        return method == TokenEndpointAuthMethod.client_secret_basic
                || method == TokenEndpointAuthMethod.client_secret_post
                || method == TokenEndpointAuthMethod.client_secret_jwt;
    }

    private static void validatePublicClient(
            OAuthClientRegistration registration,
            TokenEndpointAuthMethod method
    ) {
        if (registration.clientType() != ClientType.PUBLIC) {
            return;
        }
        if (method != TokenEndpointAuthMethod.none) {
            throw new PublicClientAuthMethodNotNoneException(method.name());
        }
        if (Boolean.TRUE.equals(registration.requireClientSecret())) {
            throw new PublicClientMustNotHaveSecretException();
        }
    }

    private static void validateConfidentialClient(
            OAuthClientRegistration registration,
            TokenEndpointAuthMethod method
    ) {
        if (registration.clientType() != ClientType.CONFIDENTIAL) {
            return;
        }
        if (method == TokenEndpointAuthMethod.none) {
            throw new InvalidClientConfigurationException(
                    "Confidential clients require clientSecret "
                            + "(token_endpoint_auth_method cannot be none)"
            );
        }
        if (method != TokenEndpointAuthMethod.private_key_jwt
                && Boolean.FALSE.equals(
                        registration.requireClientSecret()
                )) {
            throw new InvalidClientConfigurationException(
                    "Confidential clients require clientSecret "
                            + "(requireClientSecret=true)"
            );
        }
    }
}
