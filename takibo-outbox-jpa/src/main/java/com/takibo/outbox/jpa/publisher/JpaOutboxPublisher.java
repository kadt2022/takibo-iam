package com.takibo.outbox.jpa.publisher;

import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.port.OutboxPublisher;
import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapper;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;

public class JpaOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(JpaOutboxPublisher.class);

    private final OutboxMessageRepository repository;
    private final Clock clock;
    private final OutboxJpaMapper mapper;

    public JpaOutboxPublisher(OutboxMessageRepository repository, Clock clock, OutboxJpaMapper mapper) {
        this.repository = repository;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Override
    public void publish(OutboxEnvelope envelope) {
        Instant now = clock.instant();

        OutboxMessageEntity entity = mapper.toEntity(envelope, now);

        log.info("Outbox publish called | impl={} eventType={} dedupKey={}",
                this.getClass().getName(),
                envelope.eventType(),
                envelope.dedupKey()
        );

        try {
            repository.save(entity);
            repository.flush();

            log.info("Outbox persisted | id={} status={} eventType={}",
                    entity.getId(),
                    entity.getStatus(),
                    entity.getEventType()
            );
        } catch (DataIntegrityViolationException e) {
            if (isDedupConflict(e)) {
                log.warn("Outbox dedup conflict ignored | dedupKey={}", envelope.dedupKey());
                return;
            }
            log.error("Outbox persist failed", e);
            throw e;
        }
    }

    private boolean isDedupConflict(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("uq_outbox_dedup_key")
                || (message.contains("outbox_messages") && message.contains("dedup_key"));
    }
}
