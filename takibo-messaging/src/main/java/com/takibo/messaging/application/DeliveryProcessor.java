package com.takibo.messaging.application;

import com.takibo.messaging.application.backoff.BackoffPolicy;
import com.takibo.messaging.application.channel.MessageChannel;
import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DeliveryProcessor {

    private static final Logger log = LoggerFactory.getLogger(DeliveryProcessor.class);

    private final MessageDeliveryRepository repository;
    private final Map<ChannelType, MessageChannel> channels;
    private final BackoffPolicy backoffPolicy;
    private final DeliveryProcessorSettings settings;
    private final Clock clock;
    private final TransactionTemplate txTemplate;
    private final MeterRegistry meterRegistry;

    public DeliveryProcessor(
            MessageDeliveryRepository repository,
            Map<ChannelType, MessageChannel> channels,
            BackoffPolicy backoffPolicy,
            DeliveryProcessorSettings settings,
            Clock clock,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.backoffPolicy = Objects.requireNonNull(backoffPolicy, "backoffPolicy");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    public boolean hasRunnable() {
        Instant now = clock.instant();
        Instant staleBefore = now.minus(settings.lockTimeout());
        return repository.existsEligible(now, staleBefore);
    }

    public int processBatch() {
        Instant now = clock.instant();
        Instant staleBefore = now.minus(settings.lockTimeout());

        List<MessageDeliveryEntity> claimed = txTemplate.execute(status ->
                repository.claimRunnable(now, staleBefore, settings.lockedBy(), settings.batchSize())
        );

        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }

        for (MessageDeliveryEntity delivery : claimed) {
            processOne(delivery);
        }

        return claimed.size();
    }

    private void processOne(MessageDeliveryEntity delivery) {
        Instant now = clock.instant();

        ChannelType channelType;
        try {
            channelType = ChannelType.valueOf(delivery.getChannel());
        } catch (Exception e) {
            failDead(delivery, "Unsupported channel: " + delivery.getChannel());
            return;
        }

        MessageChannel channel = channels.get(channelType);
        if (channel == null) {
            failDead(delivery, "No channel registered for: " + channelType);
            return;
        }

        MessageChannel.ChannelResult result = channel.send(delivery);

        if (result.status() == DeliveryStatus.SENT) {
            txTemplate.execute(status -> {
                repository.updateStatus(delivery.getId(), DeliveryStatus.SENT, now);
                return null;
            });
            meterRegistry.counter("takibo_messaging_delivery_sent", "channel", channelType.name()).increment();
            return;
        }

        int attempts = delivery.getAttempts() + 1;
        if (attempts >= settings.maxAttempts() || result.status() == DeliveryStatus.DEAD) {
            String err = result.error() != null ? result.error() : "delivery failed";
            failDead(delivery, err);
            meterRegistry.counter("takibo_messaging_delivery_dead", "channel", channelType.name()).increment();
            return;
        }

        Instant next = now.plus(backoffPolicy.nextDelay(attempts));
        String err = result.error() != null ? result.error() : "delivery failed";

        txTemplate.execute(status -> {
            repository.updateAttempt(delivery.getId(), DeliveryStatus.FAILED, attempts, next, err, now);
            return null;
        });

        meterRegistry.counter("takibo_messaging_delivery_failed", "channel", channelType.name()).increment();
    }

    private void failDead(MessageDeliveryEntity delivery, String error) {
        Instant now = clock.instant();
        int attempts = delivery.getAttempts() + 1;
        String err = error != null && error.length() > 500 ? error.substring(0, 500) : error;

        txTemplate.execute(status -> {
            repository.updateAttempt(delivery.getId(), DeliveryStatus.DEAD, attempts, now, err, now);
            return null;
        });

        log.warn("Messaging delivery marked DEAD | id={} type={} recipient={} error={}",
                delivery.getId(), delivery.getMessageType(), delivery.getRecipientValue(), err);
    }

    public record DeliveryProcessorSettings(int batchSize, int maxAttempts, java.time.Duration lockTimeout, String lockedBy) { }
}
