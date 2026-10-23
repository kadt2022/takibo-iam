package com.takibo.outbox.jpa.mapper;

import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.model.OutboxMessage;
import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mapper MapStruct réellement généré : ce que le processeur et le publisher manipulent. */
class OutboxJpaMapperTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SPACE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final Instant CREATED = Instant.parse("2026-10-22T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private final OutboxJpaMapper mapper = new OutboxJpaMapperImpl();

    private static OutboxMessageEntity processingEntity() {
        OutboxMessageEntity e = new OutboxMessageEntity();
        e.setId(ID);
        e.setEventType("SPACE_CREATED");
        e.setAggregateType("SPACE");
        e.setAggregateId("s-1");
        e.setOrgId(ORG);
        e.setSpaceId(SPACE);
        e.setPayloadJson("{\"k\":1}");
        e.setStatus(OutboxStatus.PROCESSING);
        e.setAttempts(3);
        e.setNextRunAt(NOW.plusSeconds(4));
        e.setLastError("boom");
        e.setLockedAt(NOW);
        e.setLockedBy("node-1");
        e.setDedupKey("dedup-1");
        e.setCreatedAt(CREATED);
        e.setUpdatedAt(NOW);
        return e;
    }

    @Test
    void toEntity_createsANewPendingMessage() {
        OutboxEnvelope envelope = new OutboxEnvelope(ID, "SPACE_CREATED", "SPACE", "s-1", ORG, SPACE, "{\"k\":1}", "dedup-1", CREATED);

        OutboxMessageEntity entity = mapper.toEntity(envelope, NOW);

        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getAggregateType()).isEqualTo("SPACE");
        assertThat(entity.getAggregateId()).isEqualTo("s-1");
        assertThat(entity.getOrgId()).isEqualTo(ORG);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE);
        assertThat(entity.getPayloadJson()).isEqualTo("{\"k\":1}");
        assertThat(entity.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(entity.getAttempts()).isZero();
        assertThat(entity.getNextRunAt()).isEqualTo(NOW);
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED);
        assertThat(entity.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void toEnvelope_exposesOnlyWhatHandlersNeed() {
        OutboxEnvelope envelope = mapper.toEnvelope(processingEntity());

        assertThat(envelope).isEqualTo(new OutboxEnvelope(ID, "SPACE_CREATED", "SPACE", "s-1", ORG, SPACE,
                "{\"k\":1}", "dedup-1", CREATED));
    }

    @Test
    void toModel_copiesTheProcessingState() {
        OutboxMessage model = mapper.toModel(processingEntity());

        assertThat(model.status()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(model.attempts()).isEqualTo(3);
        assertThat(model.nextRunAt()).isEqualTo(NOW.plusSeconds(4));
        assertThat(model.lastError()).isEqualTo("boom");
        assertThat(model.lockedAt()).isEqualTo(NOW);
        assertThat(model.lockedBy()).isEqualTo("node-1");
        assertThat(model.updatedAt()).isEqualTo(NOW);
        assertThat(model.payloadJson()).isEqualTo("{\"k\":1}");
    }

    @Test
    void nullInputs_mapToNull() {
        assertThat(mapper.toEntity(null, null)).isNull();
        assertThat(mapper.toEnvelope(null)).isNull();
        assertThat(mapper.toModel(null)).isNull();
    }
}
