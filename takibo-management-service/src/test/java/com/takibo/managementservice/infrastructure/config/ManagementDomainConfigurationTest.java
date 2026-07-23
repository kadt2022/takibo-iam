package com.takibo.managementservice.infrastructure.config;

import com.takibo.managementservice.domain.service.SpaceCreationDomainService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementDomainConfigurationTest {

    @Test
    void exposes_the_pure_domain_service_as_a_spring_bean() {
        assertThat(new ManagementDomainConfiguration().spaceCreationDomainService())
                .isInstanceOf(SpaceCreationDomainService.class);
    }
}
