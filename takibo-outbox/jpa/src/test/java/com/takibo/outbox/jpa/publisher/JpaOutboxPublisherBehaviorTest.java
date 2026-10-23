package com.takibo.outbox.jpa.publisher;

import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapperImpl;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Publication réelle (vrai mapper MapStruct généré) sur un repository simulé : ce qui est persisté,
 * et le traitement des conflits de déduplication. La contrainte SQL elle-même est vérifiée par
 * OutboxJpaRepositoryIntegrationTest sur PostgreSQL.
 */
class JpaOutboxPublisherBehaviorTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-10-22T09:59:00Z");

    private final OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
    private final JpaOutboxPublisher publisher =
            new JpaOutboxPublisher(repository, Clock.fixed(NOW, ZoneOffset.UTC), new OutboxJpaMapperImpl());

    private static OutboxEnvelope envelope(String dedupKey) {
        return new OutboxEnvelope(UUID.fromString("00000000-0000-0000-0000-000000000001"), "SPACE_CREATED",
                "SPACE", "s-1", null, null, "{\"k\":1}", dedupKey, CREATED);
    }

    private static DataIntegrityViolationException violation(String rootMessage) {
        return new DataIntegrityViolationException("could not execute statement", new RuntimeException(rootMessage));
    }

    @Test
    void publish_persistsAPendingMessageRunnableNow_andFlushesWithinTheCallerTransaction() {
        publisher.publish(envelope("dedup-1"));

        ArgumentCaptor<OutboxMessageEntity> saved = ArgumentCaptor.forClass(OutboxMessageEntity.class);
        InOrder order = inOrder(repository);
        order.verify(repository).save(saved.capture());
        order.verify(repository).flush();

        OutboxMessageEntity entity = saved.getValue();
        assertThat(entity.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(entity.getEventType()).isEqualTo("SPACE_CREATED");
        assertThat(entity.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(entity.getAttempts()).isZero();
        assertThat(entity.getNextRunAt()).isEqualTo(NOW);
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED);
        assertThat(entity.getUpdatedAt()).isEqualTo(NOW);
        assertThat(entity.getDedupKey()).isEqualTo("dedup-1");
        assertThat(entity.getLockedAt()).isNull();
        assertThat(entity.getLockedBy()).isNull();
        assertThat(entity.getLastError()).isNull();
    }

    @Test
    void dedupConflict_isIgnored_soTheSameEventIsPublishedOnce() {
        when(repository.save(any())).thenThrow(violation("duplicate key value violates unique constraint \"uq_outbox_dedup_key\""));

        assertThatCode(() -> publisher.publish(envelope("dedup-1"))).doesNotThrowAnyException();
    }

    @Test
    void dedupConflictDetectedAtFlush_isAlsoIgnored() {
        doThrow(violation("ERROR: duplicate key on outbox_messages (dedup_key)")).when(repository).flush();

        assertThatCode(() -> publisher.publish(envelope("dedup-1"))).doesNotThrowAnyException();
    }

    @Test
    void otherIntegrityViolations_arePropagated_soTheCallerTransactionRollsBack() {
        DataIntegrityViolationException notNull = violation("null value in column \"payload_json\" violates not-null constraint");
        when(repository.save(any())).thenThrow(notNull);

        assertThatThrownBy(() -> publisher.publish(envelope(null))).isSameAs(notNull);
    }

    @Test
    void violationWithoutMessage_isPropagated() {
        DataIntegrityViolationException noMessage = new DataIntegrityViolationException("x", new RuntimeException((String) null));
        when(repository.save(any())).thenThrow(noMessage);

        assertThatThrownBy(() -> publisher.publish(envelope("dedup-1"))).isSameAs(noMessage);
        verify(repository).save(any());
    }
}
