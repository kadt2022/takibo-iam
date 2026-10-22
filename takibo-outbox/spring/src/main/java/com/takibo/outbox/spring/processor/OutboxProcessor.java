package com.takibo.outbox.spring.processor;

import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.core.port.OutboxBackoffPolicy;
import com.takibo.outbox.core.port.OutboxHandler;
import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapper;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import com.takibo.outbox.spring.config.OutboxProperties;
import com.takibo.outbox.spring.registry.OutboxHandlerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxMessageRepository repository;
    private final OutboxHandlerRegistry registry;
    private final OutboxBackoffPolicy backoffPolicy;
    private final OutboxProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate txTemplate;
    private final OutboxJpaMapper mapper;

    public OutboxProcessor(
            OutboxMessageRepository repository,
            OutboxHandlerRegistry registry,
            OutboxBackoffPolicy backoffPolicy,
            OutboxProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry,
            OutboxJpaMapper mapper
    ) {
        this.repository = repository;
        this.registry = registry;
        this.backoffPolicy = backoffPolicy;
        this.properties = properties;
        this.clock = clock;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.meterRegistry = meterRegistry;
        this.mapper = mapper;
    }

    public int processOnce() {
        if (!properties.isEnabled()) {
            return 0;
        }

        Instant now = clock.instant();
        Instant staleBefore = now.minus(properties.getProcessor().getLockTimeout());

        if (!repository.existsEligible(now, staleBefore)) {
            log.trace("No eligible outbox messages");
            return 0;
        }

        String lockedBy = properties.getProcessor().getLockedBy();
        int batchSize = properties.getProcessor().getBatchSize();

        List<OutboxMessageEntity> batch = txTemplate.execute(status ->
                repository.claimRunnable(now, staleBefore, lockedBy, batchSize)
        );

        if (batch == null || batch.isEmpty()) {
            return 0;
        }

        log.debug("Processing {} outbox messages", batch.size());

        for (OutboxMessageEntity entity : batch) {
            processSingle(entity);
        }

        return batch.size();
    }

    private void processSingle(OutboxMessageEntity entity) {
        Instant now = clock.instant();
        OutboxEnvelope envelope = mapper.toEnvelope(entity);

        OutboxHandler handler = registry.getOrNull(entity.getEventType());
        if (handler == null) {
            String reason = "Unsupported eventType: " + entity.getEventType();
            markDead(entity, reason, now);
            return;
        }

        try {
            handler.handle(envelope);
            markProcessed(entity, now);
        } catch (Exception ex) {
            handleFailure(entity, ex, now);
        }
    }

    private void markProcessed(OutboxMessageEntity entity, Instant now) {
        txTemplate.executeWithoutResult(status ->
                repository.updateStatus(entity.getId(), OutboxStatus.PROCESSED, now)
        );
        meterRegistry.counter("takibo_outbox_processed_total", "eventType", entity.getEventType()).increment();
    }

    private void handleFailure(OutboxMessageEntity entity, Exception ex, Instant now) {
        int attempts = entity.getAttempts() + 1;
        int maxAttempts = properties.getProcessor().getMaxAttempts();

        String error = truncateError(ex);

        if (attempts >= maxAttempts) {
            markDead(entity, "maxAttempts reached; lastError=" + error, now);
            meterRegistry.counter("takibo_outbox_dead_total", "eventType", entity.getEventType()).increment();
            return;
        }

        Duration delay = backoffPolicy.nextDelay(attempts);
        Instant nextRunAt = now.plus(delay);

        txTemplate.executeWithoutResult(status ->
                repository.failAndScheduleRetry(entity.getId(), OutboxStatus.FAILED, attempts, nextRunAt, error, now)
        );

        meterRegistry.counter("takibo_outbox_failed_total", "eventType", entity.getEventType()).increment();

        log.warn(
                "Outbox message failed | id={} eventType={} aggregateType={} aggregateId={} attempts={} nextRunAt={} error={}",
                entity.getId(),
                entity.getEventType(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                attempts,
                nextRunAt,
                error
        );
    }

    private void markDead(OutboxMessageEntity entity, String reason, Instant now) {
        int attempts = Math.max(entity.getAttempts(), 0);

        String payloadLog = "";
        if (properties.isDeadLetterLogPayload()) {
            payloadLog = " payload=" + safePayload(entity.getPayloadJson());
        }

        txTemplate.executeWithoutResult(status ->
                repository.markDead(entity.getId(), OutboxStatus.DEAD, attempts, truncate(reason), now)
        );

        log.error(
                "Outbox message dead-lettered | id={} eventType={} aggregateType={} aggregateId={} attempts={} reason={}{}",
                entity.getId(),
                entity.getEventType(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                attempts,
                reason,
                payloadLog
        );
    }

    private String truncateError(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex.getClass().getSimpleName();
        }
        return truncate(msg);
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    private String safePayload(String payloadJson) {
        if (payloadJson == null) {
            return "null";
        }
        return payloadJson.length() <= 2000 ? payloadJson : payloadJson.substring(0, 2000) + "...";
    }
}
