package com.takibo.managementservice.infrastructure.config;

import com.takibo.managementservice.domain.service.SpaceCreationDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ManagementDomainConfiguration {

    @Bean
    SpaceCreationDomainService spaceCreationDomainService() {
        return new SpaceCreationDomainService();
    }
}
