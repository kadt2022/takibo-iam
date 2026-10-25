package com.takibo.outbox.jpa.publisher;

import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapperImpl;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Publication réelle (vrai mapper MapStruct généré) sur un repository simulé : ce qui est inséré,
 * et la décision prise quand la clé de déduplication existe déjà. La sémantique SQL (ON CONFLICT,
 * transaction de l'appelant, concurrence) est vérifiée par OutboxJpaRepositoryIntegrationTest.
 */
class JpaOutboxPublisherBehaviorTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-10-22T09:59:00Z");

    private final OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
    private final JpaOutboxPublisher publisher =
            new JpaOutboxPublisher(repository, Clock.fixed(NOW, ZoneOffset.UTC), new OutboxJpaMapperImpl());

    private static OutboxEnvelope envelope(String dedupKey) {
        return new OutboxEnvelope(ID, "SPACE_CREATED", "SPACE", "s-1", ORG, null, "{\"k\":1}", dedupKey, CREATED);
    }

    private void insertReturns(int rows) {
        when(repository.insertIgnoringDedupConflict(any())).thenReturn(rows);
    }

    @Test
    void publish_insertsAPendingMessageRunnableNow_withTheEnvelopeValues() {
        insertReturns(1);

        publisher.publish(envelope("dedup-1"));

        ArgumentCaptor<OutboxMessageEntity> inserted = ArgumentCaptor.forClass(OutboxMessageEntity.class);
        verify(repository).insertIgnoringDedupConflict(inserted.capture());
        verify(repository, never()).existsSameEventForDedupKey(any());

        OutboxMessageEntity entity = inserted.getValue();
        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getEventType()).isEqualTo("SPACE_CREATED");
        assertThat(entity.getAggregateType()).isEqualTo("SPACE");
        assertThat(entity.getAggregateId()).isEqualTo("s-1");
        assertThat(entity.getOrgId()).isEqualTo(ORG);
        assertThat(entity.getSpaceId()).isNull();
        assertThat(entity.getPayloadJson()).isEqualTo("{\"k\":1}");
        assertThat(entity.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(entity.getAttempts()).isZero();
        assertThat(entity.getNextRunAt()).isEqualTo(NOW);
        assertThat(entity.getDedupKey()).isEqualTo("dedup-1");
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED);
        assertThat(entity.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void duplicateOfTheSameEvent_isIgnored() {
        insertReturns(0);
        when(repository.existsSameEventForDedupKey(any())).thenReturn(true);

        assertThatCode(() -> publisher.publish(envelope("dedup-1"))).doesNotThrowAnyException();
    }

    @Test
    void sameDedupKeyForADifferentEvent_isRejected() {
        insertReturns(0);
        when(repository.existsSameEventForDedupKey(any())).thenReturn(false);

        assertThatThrownBy(() -> publisher.publish(envelope("dedup-1")))
                .isInstanceOf(OutboxDedupKeyCollisionException.class)
                .hasMessageContaining("dedup-1");
    }

    @Test
    void persistenceFailures_arePropagated_soTheCallerTransactionRollsBack() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("duplicate key on outbox_messages_pkey");
        when(repository.insertIgnoringDedupConflict(any())).thenThrow(failure);

        assertThatThrownBy(() -> publisher.publish(envelope(null))).isSameAs(failure);
    }
}
