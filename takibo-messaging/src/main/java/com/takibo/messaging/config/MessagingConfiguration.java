package com.takibo.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.messaging.application.*;
import com.takibo.messaging.application.backoff.BackoffPolicy;
import com.takibo.messaging.application.backoff.ExponentialBackoffPolicy;
import com.takibo.messaging.application.channel.MessageChannel;
import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.infrastructure.channel.NoopChannel;
import com.takibo.messaging.infrastructure.email.SmtpEmailChannel;
import com.takibo.messaging.infrastructure.health.MessagingHealthIndicator;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryRepository;
import com.takibo.messaging.infrastructure.observability.MessagingMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration du système de messaging avec fallback robuste.
 *
 * Utilise l'injection directe de JavaMailSender via @Autowired(required = false)
 * pour éviter les problèmes d'ordre de création des beans.
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "takibo.messaging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfiguration {

    private final JavaMailSender mailSender;

    /**
     * Injection du JavaMailSender de manière optionnelle.
     * Si absent, mailSender sera null et on utilisera NoopChannel.
     */
    @Autowired
    public MessagingConfiguration(
            @Autowired(required = false) JavaMailSender mailSender,
            ApplicationContext applicationContext
    ) {
        this.mailSender = mailSender;

        // Debug : Afficher tous les beans JavaMailSender disponibles
        log.info("=== MESSAGING CONFIGURATION DEBUG ===");
        String[] mailSenderBeans = applicationContext.getBeanNamesForType(JavaMailSender.class);
        if (mailSenderBeans.length > 0) {
            log.info(" Found {} JavaMailSender bean(s): {}", mailSenderBeans.length, String.join(", ", mailSenderBeans));
            log.info(" Injected JavaMailSender: {}", mailSender != null ? mailSender.getClass().getName() : "null");
        } else {
            log.warn(" NO JavaMailSender beans found in application context!");
            log.warn(" Check if spring-boot-starter-mail is in dependencies");
            log.warn(" Check if spring.mail.* properties are configured");
        }
        log.info("====================================");
    }

    @Bean
    @ConditionalOnMissingBean
    public TemplateEngine templateEngine() {
        return new TemplateEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public PropertiesMessageCatalog messageCatalog(MessagingProperties properties) {
        return new PropertiesMessageCatalog(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public PropertyRecipientResolver propertyRecipientResolver(MessagingProperties properties) {
        return new PropertyRecipientResolver(properties);
    }

    /**
     * Map des canaux - créé directement avec le mailSender injecté.
     * Plus besoin de @ConditionalOnBean qui ne fonctionnait pas.
     */
    @Bean
    @ConditionalOnMissingBean
    public Map<ChannelType, MessageChannel> messageChannels() {
        Map<ChannelType, MessageChannel> map = new EnumMap<>(ChannelType.class);

        MessageChannel emailChannel;

        if (mailSender != null) {
            // SMTP disponible - utiliser le vrai canal
            emailChannel = new SmtpEmailChannel(mailSender);
            log.info(" Message channels configured: EMAIL -> SmtpEmailChannel (SMTP ACTIVE)");
            log.info(" Using JavaMailSender: {}", mailSender.getClass().getName());
        } else {
            // SMTP indisponible - utiliser le fallback
            emailChannel = new NoopChannel(ChannelType.EMAIL);
            log.warn(" Message channels configured: EMAIL -> NoopChannel (SMTP DISABLED)");
            log.warn(" Emails will be LOGGED but NOT SENT!");
            log.warn(" To enable SMTP:");
            log.warn("   1. Add spring-boot-starter-mail to dependencies");
            log.warn("   2. Configure spring.mail.host, spring.mail.port, etc.");
        }

        map.put(ChannelType.EMAIL, emailChannel);
        return map;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "takibo.messaging",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public MessagingDispatcher messagingDispatcher(
            MessageDeliveryRepository repository,
            java.util.List<RecipientResolver> recipientResolvers,
            Map<ChannelType, MessageChannel> channels,
            MessageCatalog catalog,
            TemplateEngine templateEngine,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        return new MessagingDispatcher(
                repository,
                recipientResolvers,
                channels,
                catalog,
                templateEngine,
                objectMapper,
                clock
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public BackoffPolicy messagingBackoffPolicy(MessagingProperties properties) {
        MessagingProperties.Backoff b = properties.getBackoff();
        if (!"exponential".equalsIgnoreCase(b.getType())) {
            throw new IllegalArgumentException(
                    "Unsupported takibo.messaging.backoff.type: " + b.getType()
            );
        }
        return new ExponentialBackoffPolicy(
                b.getBaseDelay(),
                b.getMaxDelay(),
                b.getMultiplier()
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "takibo.messaging.processor",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public DeliveryProcessor deliveryProcessor(
            MessageDeliveryRepository repository,
            Map<ChannelType, MessageChannel> channels,
            BackoffPolicy backoffPolicy,
            MessagingProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry
    ) {
        MessagingProperties.Processor p = properties.getProcessor();
        DeliveryProcessor.DeliveryProcessorSettings settings =
                new DeliveryProcessor.DeliveryProcessorSettings(
                        p.getBatchSize(),
                        p.getMaxAttempts(),
                        p.getLockTimeout(),
                        p.getLockedBy()
                );
        return new DeliveryProcessor(
                repository,
                channels,
                backoffPolicy,
                settings,
                clock,
                transactionManager,
                meterRegistry
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "takibo.messaging.processor",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public DeliveryScheduler deliveryScheduler(DeliveryProcessor processor) {
        log.info(" Starting DeliveryScheduler");
        return new DeliveryScheduler(processor);
    }

    @Bean
    public MessagingHealthIndicator messagingHealthIndicator(
            MessageDeliveryRepository repository
    ) {
        return new MessagingHealthIndicator(repository, 0);
    }

    @Bean
    public MessagingMetricsBinder messagingMetricsBinder(
            MessageDeliveryRepository repository,
            MeterRegistry meterRegistry
    ) {
        return new MessagingMetricsBinder(repository, meterRegistry);
    }
}
