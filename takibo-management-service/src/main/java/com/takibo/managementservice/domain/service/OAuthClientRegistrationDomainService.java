package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.OAuthClientRegistrationPlan;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.model.ValidatedSets;
import com.takibo.managementservice.domain.normalization.OAuthClientCollectionsNormalizer;
import com.takibo.managementservice.domain.policy.OAuthClientAuthenticationPolicy;
import com.takibo.managementservice.domain.policy.OAuthClientCredentialsProfilePolicy;
import com.takibo.managementservice.domain.policy.OAuthClientGrantPolicy;
import com.takibo.managementservice.domain.validation.OAuthClientConfigurationValidator;

public final class OAuthClientRegistrationDomainService {

    private final OAuthClientConfigurationValidator configurationValidator;
    private final OAuthClientCredentialsProfilePolicy credentialsProfilePolicy;
    private final OAuthClientAuthenticationPolicy authenticationPolicy;
    private final OAuthClientCollectionsNormalizer collectionsNormalizer;
    private final OAuthClientGrantPolicy grantPolicy;

    public OAuthClientRegistrationDomainService(
            OAuthClientConfigurationValidator configurationValidator
    ) {
        this(
                configurationValidator,
                new OAuthClientCredentialsProfilePolicy(),
                new OAuthClientAuthenticationPolicy(),
                new OAuthClientCollectionsNormalizer(),
                new OAuthClientGrantPolicy()
        );
    }

    public OAuthClientRegistrationDomainService(
            OAuthClientConfigurationValidator configurationValidator,
            OAuthClientCredentialsProfilePolicy credentialsProfilePolicy,
            OAuthClientAuthenticationPolicy authenticationPolicy,
            OAuthClientCollectionsNormalizer collectionsNormalizer,
            OAuthClientGrantPolicy grantPolicy
    ) {
        this.configurationValidator = configurationValidator;
        this.credentialsProfilePolicy = credentialsProfilePolicy;
        this.authenticationPolicy = authenticationPolicy;
        this.collectionsNormalizer = collectionsNormalizer;
        this.grantPolicy = grantPolicy;
    }

    public OAuthClientRegistrationPlan prepareRegistration(
            OAuthClientRegistration registration
    ) {
        OAuthClientRegistration normalized =
                credentialsProfilePolicy.normalizeAndValidate(registration);
        configurationValidator.validateRegistration(normalized);

        TokenEndpointAuthMethod authMethod =
                authenticationPolicy.resolveAuthMethod(normalized);
        authenticationPolicy.validateAuthenticationProfile(
                normalized,
                authMethod
        );

        ValidatedSets sets =
                collectionsNormalizer.normalizeCollections(normalized);
        boolean requirePkce = grantPolicy.resolvePkce(normalized, sets);
        grantPolicy.validateGrantProfile(
                normalized,
                authMethod,
                sets,
                requirePkce
        );

        return new OAuthClientRegistrationPlan(
                normalized,
                authMethod,
                requirePkce,
                authenticationPolicy.requiresSecret(normalized, authMethod),
                sets
        );
    }
}
