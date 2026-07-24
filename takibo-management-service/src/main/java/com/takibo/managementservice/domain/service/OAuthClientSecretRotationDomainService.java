package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.policy.OAuthClientAuthenticationPolicy;
import com.takibo.managementservice.domain.validation.OAuthClientConfigurationValidator;

import java.time.Instant;

public final class OAuthClientSecretRotationDomainService {

    private final OAuthClientConfigurationValidator configurationValidator;
    private final OAuthClientAuthenticationPolicy authenticationPolicy;

    public OAuthClientSecretRotationDomainService(
            OAuthClientConfigurationValidator configurationValidator
    ) {
        this(
                configurationValidator,
                new OAuthClientAuthenticationPolicy()
        );
    }

    public OAuthClientSecretRotationDomainService(
            OAuthClientConfigurationValidator configurationValidator,
            OAuthClientAuthenticationPolicy authenticationPolicy
    ) {
        this.configurationValidator = configurationValidator;
        this.authenticationPolicy = authenticationPolicy;
    }

    public void validateRequestedExpiration(Instant expiresAt) {
        configurationValidator.validateSecretExpiration(expiresAt);
    }

    public Instant resolveExpiration(
            OAuthClient client,
            Instant requestedExpiration
    ) {
        if (client.getClientType() == ClientType.PUBLIC
                || !authenticationPolicy.usesSecret(
                        client.getTokenEndpointAuthMethod()
                )) {
            throw new InvalidClientConfigurationException(
                    "client does not use secrets"
            );
        }

        Instant resolved = requestedExpiration != null
                ? requestedExpiration
                : client.getClientSecretExpiresAt();
        configurationValidator.validateSecretExpiration(resolved);
        return resolved;
    }
}
