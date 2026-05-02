package com.takibo.audit.core;

import com.takibo.audit.infrastructure.entity.AuditEvent;
import com.takibo.audit.api.AuditEventStore;
import com.takibo.audit.config.AuditStorageProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.util.Collections;
import java.util.List;


@Configuration
@EnableConfigurationProperties(AuditStorageProperties.class)
@ConditionalOnClass(AuditEvent.class)
public class AuditStoreAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public SmartAuditStoreRouter auditStoreRouter(
            ObjectProvider<List<AuditEventStore>> storesProvider,
            AuditStorageProperties properties,
            MeterRegistry registry
    ) {
        List<AuditEventStore> stores = storesProvider.getIfAvailable(Collections::emptyList);
        return new SmartAuditStoreRouter(
                stores.stream()
                        .filter(s -> isEnabled(s, properties))
                        .toList(),
                registry,
                SmartAuditStoreRouter.Mode.valueOf(properties.mode().name())
        );
    }

    private boolean isEnabled(AuditEventStore store, AuditStorageProperties props) {
        return props.stores().stream()
                .anyMatch(c -> c.name().equalsIgnoreCase(store.getName()) && c.enabled());
    }
}
