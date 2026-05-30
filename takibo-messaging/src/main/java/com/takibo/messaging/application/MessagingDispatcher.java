package com.takibo.messaging.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.messaging.application.backoff.BackoffPolicy;
import com.takibo.messaging.application.channel.MessageChannel;
import com.takibo.messaging.config.MessagingProperties;
import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.domain.MessageAction;
import com.takibo.messaging.domain.Recipient;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


public class MessagingDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MessagingDispatcher.class);

    private final MessageDeliveryRepository repository;
    private final List<RecipientResolver> recipientResolvers;
    private final Map<ChannelType, MessageChannel> channels;
    private final MessageCatalog catalog;
    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MessagingDispatcher(
            MessageDeliveryRepository repository,
            List<RecipientResolver> recipientResolvers,
            Map<ChannelType, MessageChannel> channels,
            MessageCatalog catalog,
            TemplateEngine templateEngine,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.recipientResolvers = Objects.requireNonNull(recipientResolvers, "recipientResolvers");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.templateEngine = Objects.requireNonNull(templateEngine, "templateEngine");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DispatchResult dispatch(MessageAction action, MessagingContext context) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(context, "context");

        MessagingProperties.MessageTemplate template = catalog.findTemplate(action.messageType())
                .orElseThrow(() -> new IllegalArgumentException("No message template configured for type: " + action.messageType()));

        ChannelType channelType = action.channelOverride() != null ? action.channelOverride() : template.getChannel();

        List<Recipient> recipients = resolveRecipients(action, context);
        if (recipients.isEmpty()) {
            log.info("Messaging dispatch skipped (no recipients) | type={} dedupKey={}", action.messageType(), action.dedupKey());
            return new DispatchResult(0, 0);
        }

        int created = 0;
        int skipped = 0;

        for (Recipient recipient : recipients) {
            String deliveryDedup = action.dedupKey() + ":" + recipient.key();

            MessageDeliveryEntity entity = buildDelivery(action, context, template, channelType, recipient, deliveryDedup);

            try {
                repository.save(entity);
                repository.flush();
                created++;
            } catch (DataIntegrityViolationException e) {
                if (isDedupConflict(e)) {
                    skipped++;
                    continue;
                }
                throw e;
            }
        }

        log.info("Messaging dispatch completed | type={} created={} skipped={} dedupKey={}",
                action.messageType(), created, skipped, action.dedupKey());

        return new DispatchResult(created, skipped);
    }

    private List<Recipient> resolveRecipients(MessageAction action, MessagingContext context) {
        for (RecipientResolver resolver : recipientResolvers) {
            if (resolver.supports(action.messageType())) {
                return resolver.resolve(action, context);
            }
        }
        return List.of();
    }

    private MessageDeliveryEntity buildDelivery(
            MessageAction action,
            MessagingContext context,
            MessagingProperties.MessageTemplate template,
            ChannelType channelType,
            Recipient recipient,
            String deliveryDedup
    ) {
        Instant now = clock.instant();

        String subject = templateEngine.render(template.getSubject(), action.attributes());
        String body = templateEngine.render(template.getBody(), action.attributes());

        MessageDeliveryEntity e = new MessageDeliveryEntity();
        e.setId(UUID.randomUUID());
        e.setOrgId(action.orgId());
        e.setSpaceId(action.spaceId());
        e.setMessageType(action.messageType());
        e.setChannel(channelType.name());
        e.setRecipientType(recipient.type());
        e.setRecipientValue(recipient.value());
        e.setRecipientKey(recipient.key());
        e.setFromAddress(template.getFrom());
        e.setSubject(subject);
        e.setBody(body);
        e.setPayloadJson(toJsonSafe(action.attributes()));
        e.setStatus(DeliveryStatus.PENDING);
        e.setAttempts(0);
        e.setNextRunAt(now);
        e.setLastError(null);
        e.setLockedAt(null);
        e.setLockedBy(null);
        e.setDedupKey(deliveryDedup);
        e.setCorrelationOutboxId(context.correlationOutboxId());
        e.setTraceId(context.traceId());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    private String toJsonSafe(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private boolean isDedupConflict(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("uq_message_deliveries_dedup_key")
                || (normalized.contains("message_deliveries") && normalized.contains("dedup_key"));
    }

    public record DispatchResult(int created, int skipped) { }
}
