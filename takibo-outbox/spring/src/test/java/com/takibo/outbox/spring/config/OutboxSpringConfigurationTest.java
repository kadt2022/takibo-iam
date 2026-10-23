package com.takibo.outbox.spring.config;

import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.port.OutboxBackoffPolicy;
import com.takibo.outbox.core.port.OutboxHandler;
import com.takibo.outbox.core.port.OutboxPublisher;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapper;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapperImpl;
import com.takibo.outbox.jpa.publisher.JpaOutboxPublisher;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import com.takibo.outbox.spring.observability.OutboxMetricsBinder;
import com.takibo.outbox.spring.processor.OutboxProcessor;
import com.takibo.outbox.spring.registry.OutboxHandlerRegistry;
import com.takibo.outbox.spring.scheduling.OutboxScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxSpringConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(Infrastructure.class, OutboxSpringConfiguration.class));

    @Configuration(proxyBeanMethods = false)
    static class Infrastructure {
        @Bean OutboxMessageRepository outboxMessageRepository() { return mock(OutboxMessageRepository.class); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean OutboxJpaMapper outboxJpaMapper() { return new OutboxJpaMapperImpl(); }
        @Bean PlatformTransactionManager transactionManager() { return mock(PlatformTransactionManager.class); }
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
        @Bean OutboxHandler spaceCreatedHandler() {
            return new OutboxHandler() {
                @Override public String eventType() { return "SPACE_CREATED"; }
                @Override public void handle(OutboxEnvelope envelope) { }
            };
        }
    }

    @Test
    void wiresTheProcessingPipeline_withDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(OutboxPublisher.class).isInstanceOf(JpaOutboxPublisher.class);
            assertThat(context).hasSingleBean(OutboxProcessor.class)
                    .hasSingleBean(OutboxScheduler.class)
                    .hasSingleBean(OutboxMetricsBinder.class)
                    .hasBean("outboxHealthIndicator");
            assertThat(context.getBean("outboxHealthIndicator")).isInstanceOf(HealthIndicator.class);
            assertThat(context.getBean(OutboxHandlerRegistry.class).getOrNull("SPACE_CREATED")).isNotNull();
            assertThat(context.getBean(OutboxBackoffPolicy.class).nextDelay(3)).isEqualTo(Duration.ofSeconds(4));
            assertThat(context.getBean(MeterRegistry.class).find("takibo_outbox_pending_count").gauge()).isNotNull();

            OutboxProperties properties = context.getBean(OutboxProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.isDeadLetterLogPayload()).isFalse();
            assertThat(properties.getProcessor().getBatchSize()).isEqualTo(50);
            assertThat(properties.getProcessor().getFixedDelay()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.getProcessor().getMaxAttempts()).isEqualTo(10);
            assertThat(properties.getProcessor().getLockedBy()).isEqualTo("takibo-outbox");
            assertThat(properties.getProcessor().isSchedulingEnabled()).isTrue();
            assertThat(properties.getProcessor().getLockTimeout()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getBackoff().getType()).isEqualTo("exponential");
            assertThat(properties.getBackoff().getBaseDelay()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.getBackoff().getMaxDelay()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.getBackoff().getMultiplier()).isEqualTo(2);
        });
    }

    @Test
    void propertiesAreBoundFromTakiboOutboxPrefix_andDriveTheBackoff() {
        runner.withPropertyValues(
                "takibo.outbox.enabled=false",
                "takibo.outbox.dead-letter-log-payload=true",
                "takibo.outbox.processor.batch-size=10",
                "takibo.outbox.processor.fixed-delay=PT3S",
                "takibo.outbox.processor.max-attempts=4",
                "takibo.outbox.processor.locked-by=node-7",
                "takibo.outbox.processor.lock-timeout=PT30S",
                "takibo.outbox.backoff.type=EXPONENTIAL",
                "takibo.outbox.backoff.base-delay=PT2S",
                "takibo.outbox.backoff.max-delay=PT5S",
                "takibo.outbox.backoff.multiplier=3"
        ).run(context -> {
            OutboxProperties properties = context.getBean(OutboxProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.isDeadLetterLogPayload()).isTrue();
            assertThat(properties.getProcessor().getBatchSize()).isEqualTo(10);
            assertThat(properties.getProcessor().getFixedDelay()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.getProcessor().getMaxAttempts()).isEqualTo(4);
            assertThat(properties.getProcessor().getLockedBy()).isEqualTo("node-7");
            assertThat(properties.getProcessor().getLockTimeout()).isEqualTo(Duration.ofSeconds(30));
            OutboxBackoffPolicy backoff = context.getBean(OutboxBackoffPolicy.class);
            assertThat(backoff.nextDelay(1)).isEqualTo(Duration.ofSeconds(2));
            assertThat(backoff.nextDelay(2)).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void schedulingCanBeDisabled_withoutDisablingTheProcessor() {
        runner.withPropertyValues("takibo.outbox.processor.scheduling-enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(OutboxScheduler.class);
            assertThat(context).hasSingleBean(OutboxProcessor.class);
        });
    }

    @Test
    void unsupportedBackoffType_failsStartup() {
        runner.withPropertyValues("takibo.outbox.backoff.type=linear").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessage("Unsupported backoff.type: linear");
        });
    }

    @Test
    void duplicateHandlers_failStartup() {
        runner.withBean("duplicateHandler", OutboxHandler.class, () -> new OutboxHandler() {
            @Override public String eventType() { return "SPACE_CREATED"; }
            @Override public void handle(OutboxEnvelope envelope) { }
        }).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessage("Duplicate OutboxHandler for eventType: SPACE_CREATED");
        });
    }
}
