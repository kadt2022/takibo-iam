package com.takibo.outbox.spring.config;

import com.takibo.outbox.core.port.OutboxBackoffPolicy;
import com.takibo.outbox.core.port.OutboxPublisher;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapper;
import com.takibo.outbox.jpa.publisher.JpaOutboxPublisher;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import com.takibo.outbox.spring.backoff.ExponentialOutboxBackoffPolicy;
import com.takibo.outbox.spring.health.OutboxHealthIndicator;
import com.takibo.outbox.spring.observability.OutboxMetricsBinder;
import com.takibo.outbox.spring.processor.OutboxProcessor;
import com.takibo.outbox.spring.registry.OutboxHandlerRegistry;
import com.takibo.outbox.spring.scheduling.OutboxScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.List;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxSpringConfiguration {

    @Bean
    public OutboxBackoffPolicy outboxBackoffPolicy(OutboxProperties properties) {
        OutboxProperties.Backoff b = properties.getBackoff();
        if (!"exponential".equalsIgnoreCase(b.getType())) {
            throw new IllegalArgumentException("Unsupported backoff.type: " + b.getType());
        }
        return new ExponentialOutboxBackoffPolicy(b.getBaseDelay(), b.getMaxDelay(), b.getMultiplier());
    }

    @Bean
    public OutboxHandlerRegistry outboxHandlerRegistry(List<com.takibo.outbox.core.port.OutboxHandler> handlers) {
        return new OutboxHandlerRegistry(handlers);
    }

    @Bean
    public OutboxPublisher outboxPublisher(
            OutboxMessageRepository repository,
            Clock clock,
            OutboxJpaMapper mapper
    ) {
        return new JpaOutboxPublisher(repository, clock, mapper);
    }

    @Bean
    public OutboxProcessor outboxProcessor(
            OutboxMessageRepository repository,
            OutboxHandlerRegistry registry,
            OutboxBackoffPolicy backoffPolicy,
            OutboxProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry,
            com.takibo.outbox.jpa.mapper.OutboxJpaMapper mapper
    ) {
        return new OutboxProcessor(
                repository,
                registry,
                backoffPolicy,
                properties,
                clock,
                transactionManager,
                meterRegistry,
                mapper
        );
    }


    @Bean
    @ConditionalOnProperty(
            prefix = "takibo.outbox.processor",
            name = "scheduling-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public OutboxScheduler outboxScheduler(OutboxProcessor processor) {
        return new OutboxScheduler(processor);
    }

    @Bean
    public OutboxMetricsBinder outboxMetricsBinder(OutboxMessageRepository repository, MeterRegistry meterRegistry) {
        return new OutboxMetricsBinder(repository, meterRegistry);
    }

    @Bean
    public HealthIndicator outboxHealthIndicator(OutboxMessageRepository repository) {
        return new OutboxHealthIndicator(repository, 0, 60.0);
    }
}
