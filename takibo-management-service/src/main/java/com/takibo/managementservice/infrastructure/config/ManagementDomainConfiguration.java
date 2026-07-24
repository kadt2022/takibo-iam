package com.takibo.managementservice.infrastructure.config;

import com.takibo.managementservice.domain.policy.OrganizationSignupPolicy;
import com.takibo.managementservice.domain.policy.SpaceCodePolicy;
import com.takibo.managementservice.domain.policy.SpaceStatusTransitionPolicy;
import com.takibo.managementservice.domain.service.OAuthClientRegistrationDomainService;
import com.takibo.managementservice.domain.service.OAuthClientSecretRotationDomainService;
import com.takibo.managementservice.domain.service.OrganizationCreationDomainService;
import com.takibo.managementservice.domain.service.SpaceCreationDomainService;
import com.takibo.managementservice.domain.validation.OAuthClientConfigurationValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ManagementDomainConfiguration {

    @Bean
    SpaceCreationDomainService spaceCreationDomainService() {
        return new SpaceCreationDomainService();
    }

    @Bean
    SpaceCodePolicy spaceCodePolicy() {
        return new SpaceCodePolicy();
    }

    @Bean
    SpaceStatusTransitionPolicy spaceStatusTransitionPolicy() {
        return new SpaceStatusTransitionPolicy();
    }

    @Bean
    OrganizationCreationDomainService organizationCreationDomainService() {
        return new OrganizationCreationDomainService();
    }

    @Bean
    OrganizationSignupPolicy organizationSignupPolicy() {
        return new OrganizationSignupPolicy();
    }

    @Bean
    OAuthClientConfigurationValidator oauthClientConfigurationValidator(Clock clock) {
        return new OAuthClientConfigurationValidator(clock);
    }

    @Bean
    OAuthClientRegistrationDomainService oauthClientRegistrationDomainService(
            OAuthClientConfigurationValidator configurationValidator
    ) {
        return new OAuthClientRegistrationDomainService(configurationValidator);
    }

    @Bean
    OAuthClientSecretRotationDomainService oauthClientSecretRotationDomainService(
            OAuthClientConfigurationValidator configurationValidator
    ) {
        return new OAuthClientSecretRotationDomainService(
                configurationValidator
        );
    }
}
