package com.takibo.iamboot;

import com.takibo.audit.config.AuditStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.takibo",
        "com.takibo.audit",
        "com.takibo.authorizationserver",
        "com.takibo.identitycore",
        "com.takibo.managementservice",
        "com.takibo.iamboot",
        "com.takibo.securitymanagement",
        "com.takibo.securitycontext",
        "com.takibo.securitycontextspring",
        "com.takibo.adp",
        "com.takibo.adp.spring",
        "com.takibo.outbox",
        "com.takibo.messaging"
})
@EntityScan(basePackages = {
        "com.takibo.identitycore.infrastructure.jpa.entity",
        "com.takibo.identitycore.infrastructure.entity",
        "com.takibo.managementservice.infrastructure.jpa.entity",
        "com.takibo.managementservice.infrastructure.entity",
        "com.takibo.authorizationserver.infrastructure.jpa.entity",
        "com.takibo.audit.infrastructure.entity",
        "com.takibo.audit.domain",
        "com.takibo.securitymanagement.infrastructure.adaptivedecision",
        "com.takibo.identitycore.domain.catalogrbac",
        "com.takibo.outbox.jpa.entity",
        "com.takibo.messaging.infrastructure.jpa.entity",
        "com.takibo.messaging.infrastructure.jpa"
})
@ConfigurationPropertiesScan(basePackages = {
        "com.takibo.identitycore.application.properties",
        "com.takibo.managementservice",
        "com.takibo.audit",
        "com.takibo.audit.config",
        "com.takibo.adp.spring.config"
})
@EnableConfigurationProperties({
        AuditStorageProperties.class
})
@EnableJpaRepositories(basePackages = {
        "com.takibo.audit.infrastructure.repository",
        "com.takibo.identitycore.infrastructure.jpa.repository",
        "com.takibo.managementservice.infrastructure.jpa.repository",
        "com.takibo.authorizationserver.infrastructure.jpa.repository",
        "com.takibo.securitymanagement.infrastructure.adaptivedecision",  // ⭐ RAJOUTÉ (pour UserBehaviorProfileRepository)
        "com.takibo.outbox.jpa.repository",
        "com.takibo.messaging.infrastructure.jpa.repository",
        "com.takibo.messaging.infrastructure.jpa"
})
@Import({com.takibo.securitymanagement.sentinel.config.SentinelConfig.class})
public class TakiboIamBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(TakiboIamBootApplication.class, args);
    }
}


