package com.takibo.managementservice.infrastructure.config;

import com.takibo.managementservice.domain.service.OAuthClientRegistrationDomainService;
import com.takibo.managementservice.domain.service.OrganizationCreationDomainService;
import com.takibo.managementservice.domain.service.SpaceCreationDomainService;
import com.takibo.managementservice.domain.validation.OAuthClientConfigurationValidator;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementDomainConfigurationTest {

    @Test
    void exposes_the_pure_domain_service_as_a_spring_bean() {
        assertThat(new ManagementDomainConfiguration().spaceCreationDomainService())
                .isInstanceOf(SpaceCreationDomainService.class);
    }

    @Test
    void exposes_oauth_domain_collaborators_as_spring_beans() {
        ManagementDomainConfiguration configuration =
                new ManagementDomainConfiguration();
        OAuthClientConfigurationValidator validator =
                configuration.oauthClientConfigurationValidator(Clock.systemUTC());

        assertThat(validator)
                .isInstanceOf(OAuthClientConfigurationValidator.class);
        assertThat(configuration.oauthClientRegistrationDomainService(validator))
                .isInstanceOf(OAuthClientRegistrationDomainService.class);
    }

    @Test
    void exposes_organization_creation_domain_service_as_a_spring_bean() {
        assertThat(new ManagementDomainConfiguration()
                .organizationCreationDomainService())
                .isInstanceOf(OrganizationCreationDomainService.class);
    }
}
