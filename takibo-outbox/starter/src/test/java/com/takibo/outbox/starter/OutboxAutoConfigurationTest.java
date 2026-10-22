package com.takibo.outbox.starter;

import com.takibo.outbox.core.port.OutboxPublisher;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapper;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapperImpl;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import com.takibo.outbox.spring.processor.OutboxProcessor;
import com.takibo.outbox.spring.scheduling.OutboxScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Infrastructure.class)
            .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class));

    @Configuration(proxyBeanMethods = false)
    static class Infrastructure {
        @Bean OutboxMessageRepository outboxMessageRepository() { return mock(OutboxMessageRepository.class); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean OutboxJpaMapper outboxJpaMapper() { return new OutboxJpaMapperImpl(); }
        @Bean PlatformTransactionManager transactionManager() { return mock(PlatformTransactionManager.class); }
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }

    @Test
    void isRegisteredAsASpringBootAutoConfiguration() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(OutboxAutoConfiguration.class.getName());
    }

    @Test
    void enabledByDefault_activatesTheOutboxPipeline() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxPublisher.class)
                    .hasSingleBean(OutboxProcessor.class)
                    .hasSingleBean(OutboxScheduler.class);
        });
    }

    @Test
    void explicitlyEnabled_activatesTheOutboxPipeline() {
        runner.withPropertyValues("takibo.outbox.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(OutboxProcessor.class));
    }

    @Test
    void disabled_registersNoOutboxBean() {
        runner.withPropertyValues("takibo.outbox.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OutboxPublisher.class)
                    .doesNotHaveBean(OutboxProcessor.class)
                    .doesNotHaveBean(OutboxScheduler.class);
        });
    }
}
