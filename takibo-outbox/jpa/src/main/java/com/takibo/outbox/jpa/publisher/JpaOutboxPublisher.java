package com.takibo.outbox.jpa.publisher;

import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.port.OutboxPublisher;
import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapper;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        int inserted = repository.insertIgnoringDedupConflict(entity);

        if (inserted == 1) {
            log.info("Outbox persisted | id={} status={} eventType={}",
                    entity.getId(),
                    entity.getStatus(),
                    entity.getEventType()
            );
            return;
        }

        // Le doublon n'est légitime que s'il décrit le même événement : une même clé portée par un
        // autre événement est une anomalie de l'appelant, qui doit faire échouer sa transaction.
        boolean sameEvent = repository.existsSameEventForDedupKey(entity);
        if (!sameEvent) {
            throw new OutboxDedupKeyCollisionException(entity.getDedupKey(), entity.getEventType());
        }
        log.warn("Outbox duplicate ignored | dedupKey={} eventType={}", entity.getDedupKey(), entity.getEventType());
    }
}
