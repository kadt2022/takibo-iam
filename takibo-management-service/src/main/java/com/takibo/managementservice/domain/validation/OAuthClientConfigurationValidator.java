package com.takibo.managementservice.domain.validation;

import com.takibo.managementservice.domain.model.OAuthClientRegistration;

import java.time.Clock;
import java.time.Instant;

public class OAuthClientConfigurationValidator {

    private final OAuthClientMetadataValidator metadataValidator;
    private final OAuthClientJwkSetValidator jwkSetValidator;

    public OAuthClientConfigurationValidator(Clock clock) {
        this(
                new OAuthClientMetadataValidator(clock),
                new OAuthClientJwkSetValidator()
        );
    }

    public OAuthClientConfigurationValidator(
            OAuthClientMetadataValidator metadataValidator,
            OAuthClientJwkSetValidator jwkSetValidator
    ) {
        this.metadataValidator = metadataValidator;
        this.jwkSetValidator = jwkSetValidator;
    }

    public void validateRegistration(OAuthClientRegistration registration) {
        metadataValidator.validateRegistration(registration);
        jwkSetValidator.validate(registration);
    }

    public void validateSecretExpiration(Instant expiresAt) {
        metadataValidator.validateSecretExpiration(expiresAt);
    }
}
