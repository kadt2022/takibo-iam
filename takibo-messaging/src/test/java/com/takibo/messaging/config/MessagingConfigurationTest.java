package com.takibo.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.messaging.application.DeliveryProcessor;
import com.takibo.messaging.application.DeliveryScheduler;
import com.takibo.messaging.application.MessageCatalog;
import com.takibo.messaging.application.MessagingDispatcher;
import com.takibo.messaging.application.PropertyRecipientResolver;
import com.takibo.messaging.application.TemplateEngine;
import com.takibo.messaging.application.backoff.BackoffPolicy;
import com.takibo.messaging.application.channel.MessageChannel;
import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.infrastructure.channel.NoopChannel;
import com.takibo.messaging.infrastructure.email.SmtpEmailChannel;
import com.takibo.messaging.infrastructure.health.MessagingHealthIndicator;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryRepository;
import com.takibo.messaging.infrastructure.observability.MessagingMetricsBinder;
import com.takibo.messaging.testsupport.FakeMessageDeliveryRepository;
import com.takibo.messaging.testsupport.RecordingTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagingConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-01-06T10:00:00Z");

    @Nested
    class FactoryMethods {

        private final MessagingProperties properties = new MessagingProperties();

        @Test
        void fallsBackToNoopEmailChannelWhenNoMailSenderIsAvailable() {
            Map<ChannelType, MessageChannel> channels = configuration(null).messageChannels();

            assertEquals(1, channels.size());
            assertInstanceOf(NoopChannel.class, channels.get(ChannelType.EMAIL));
            assertEquals(ChannelType.EMAIL, channels.get(ChannelType.EMAIL).channelType());
        }

        @Test
        void usesSmtpEmailChannelWhenMailSenderIsAvailable() {
            Map<ChannelType, MessageChannel> channels = configuration(new JavaMailSenderImpl()).messageChannels();

            assertInstanceOf(SmtpEmailChannel.class, channels.get(ChannelType.EMAIL));
        }

        @Test
        void buildsExponentialBackoffFromProperties() {
            properties.getBackoff().setType("EXPONENTIAL");
            properties.getBackoff().setBaseDelay(Duration.ofSeconds(3));
            properties.getBackoff().setMaxDelay(Duration.ofSeconds(20));
            properties.getBackoff().setMultiplier(3);

            BackoffPolicy policy = configuration(null).messagingBackoffPolicy(properties);

            assertEquals(Duration.ofSeconds(3), policy.nextDelay(1));
            assertEquals(Duration.ofSeconds(9), policy.nextDelay(2));
            assertEquals(Duration.ofSeconds(20), policy.nextDelay(3));
        }

        @Test
        void rejectsUnsupportedBackoffType() {
            properties.getBackoff().setType("linear");
            MessagingConfiguration configuration = configuration(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> configuration.messagingBackoffPolicy(properties));
            assertEquals("Unsupported takibo.messaging.backoff.type: linear", ex.getMessage());
        }

        @Test
        void rejectsMissingBackoffType() {
            properties.getBackoff().setType(null);
            MessagingConfiguration configuration = configuration(null);

            assertThrows(IllegalArgumentException.class, () -> configuration.messagingBackoffPolicy(properties));
        }

        @Test
        void healthIndicatorWarnsAsSoonAsOneDeliveryIsDead() {
            FakeMessageDeliveryRepository fake = new FakeMessageDeliveryRepository();
            fake.countsByStatus.put(com.takibo.messaging.domain.DeliveryStatus.DEAD, 1L);

            MessagingHealthIndicator indicator = configuration(null).messagingHealthIndicator(fake.repository());

            assertEquals("WARN", indicator.health().getStatus().getCode());
        }

        private MessagingConfiguration configuration(JavaMailSender mailSender) {
            GenericApplicationContext context = new GenericApplicationContext();
            if (mailSender != null) {
                context.registerBean("mailSender", JavaMailSender.class, () -> mailSender);
            }
            context.refresh();
            try {
                return new MessagingConfiguration(mailSender, context);
            } finally {
                context.close();
            }
        }
    }

    @Nested
    class ContextWiring {

        private final FakeMessageDeliveryRepository fakeRepository = new FakeMessageDeliveryRepository();
        private AnnotationConfigApplicationContext context;

        @AfterEach
        void closeContext() {
            if (context != null) {
                context.close();
            }
        }

        @Test
        void registersCompletePipelineByDefault() {
            start(Map.of(), false);

            assertEquals(1, context.getBeansOfType(MessagingDispatcher.class).size());
            assertEquals(1, context.getBeansOfType(DeliveryProcessor.class).size());
            assertEquals(1, context.getBeansOfType(DeliveryScheduler.class).size());
            assertEquals(1, context.getBeansOfType(MessagingHealthIndicator.class).size());
            assertEquals(1, context.getBeansOfType(MessagingMetricsBinder.class).size());
            assertEquals(1, context.getBeansOfType(PropertyRecipientResolver.class).size());
            assertEquals(1, context.getBeansOfType(TemplateEngine.class).size());
            assertNotNull(context.getBean(MeterRegistry.class).find("takibo_messaging_dead_count").gauge());
        }

        @Test
        void usesNoopChannelWithoutMailSenderBean() {
            start(Map.of("takibo.messaging.processor.enabled", "false"), false);

            assertInstanceOf(NoopChannel.class, channels().get(ChannelType.EMAIL));
        }

        @Test
        void usesSmtpChannelWhenMailSenderBeanExists() {
            start(Map.of("takibo.messaging.processor.enabled", "false"), true);

            assertInstanceOf(SmtpEmailChannel.class, channels().get(ChannelType.EMAIL));
        }

        @Test
        void processorCanBeDisabledWhileDispatchStaysAvailable() {
            start(Map.of("takibo.messaging.processor.enabled", "false"), false);

            assertTrue(context.getBeansOfType(DeliveryProcessor.class).isEmpty());
            assertTrue(context.getBeansOfType(DeliveryScheduler.class).isEmpty());
            assertEquals(1, context.getBeansOfType(MessagingDispatcher.class).size());
        }

        @Test
        void wholeModuleCanBeDisabled() {
            start(Map.of("takibo.messaging.enabled", "false"), false);

            assertTrue(context.getBeansOfType(MessagingConfiguration.class).isEmpty());
            assertTrue(context.getBeansOfType(MessagingDispatcher.class).isEmpty());
            assertTrue(context.getBeansOfType(DeliveryProcessor.class).isEmpty());
        }

        @Test
        void bindsCatalogFromProperties() {
            start(Map.of(
                    "takibo.messaging.processor.enabled", "false",
                    "takibo.messaging.catalog.messages[WELCOME_SPACE].channel", "IN_APP",
                    "takibo.messaging.catalog.messages[WELCOME_SPACE].subject", "Welcome ${name}",
                    "takibo.messaging.catalog.messages[WELCOME_SPACE].recipients[0]", "ops@takibo.test"
            ), false);

            MessagingProperties.MessageTemplate template = context.getBean(MessageCatalog.class)
                    .findTemplate("WELCOME_SPACE").orElseThrow();
            assertEquals(ChannelType.IN_APP, template.getChannel());
            assertEquals("Welcome ${name}", template.getSubject());
            assertEquals(java.util.List.of("ops@takibo.test"), template.getRecipients());
        }

        @Test
        void processorUsesBoundProcessorSettings() {
            start(Map.of(
                    "takibo.messaging.processor.batch-size", "7",
                    "takibo.messaging.processor.lock-timeout", "45s",
                    "takibo.messaging.processor.locked-by", "node-42",
                    "takibo.messaging.processor.fixed-delay", "1h"
            ), false);

            context.getBean(DeliveryProcessor.class).processBatch();

            FakeMessageDeliveryRepository.ClaimCall claim = fakeRepository.claimCalls.get(0);
            assertEquals(7, claim.batchSize());
            assertEquals("node-42", claim.lockedBy());
            assertEquals(NOW.minusSeconds(45), claim.staleBefore());
        }

        @Test
        void failsFastOnUnsupportedBackoffType() {
            Map<String, Object> props = Map.of(
                    "takibo.messaging.processor.enabled", "false",
                    "takibo.messaging.backoff.type", "fibonacci");

            BeanCreationException ex = assertThrows(BeanCreationException.class, () -> start(props, false));
            assertInstanceOf(IllegalArgumentException.class, ex.getMostSpecificCause());
        }

        @SuppressWarnings("unchecked")
        private Map<ChannelType, MessageChannel> channels() {
            return (Map<ChannelType, MessageChannel>) context.getBean("messageChannels");
        }

        private void start(Map<String, Object> properties, boolean withMailSender) {
            context = new AnnotationConfigApplicationContext();
            Map<String, Object> source = new HashMap<>(properties);
            source.putIfAbsent("takibo.messaging.processor.fixed-delay", "1h");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", source));
            context.registerBean(MessageDeliveryRepository.class, fakeRepository::repository);
            context.registerBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC));
            context.registerBean(ObjectMapper.class);
            Supplier<PlatformTransactionManager> transactions = RecordingTransactionManager::new;
            context.registerBean(PlatformTransactionManager.class, transactions);
            context.registerBean(MeterRegistry.class, SimpleMeterRegistry::new);
            if (withMailSender) {
                context.registerBean(JavaMailSender.class, JavaMailSenderImpl::new);
            }
            context.register(MessagingConfiguration.class);
            context.refresh();
        }
    }
}
