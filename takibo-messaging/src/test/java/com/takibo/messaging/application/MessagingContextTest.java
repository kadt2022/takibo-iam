package com.takibo.messaging.application;

import com.takibo.outbox.core.model.OutboxEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessagingContextTest {

    @Test
    void noneHasNoCorrelationAndNoTrace() {
        MessagingContext context = MessagingContext.none();

        assertNull(context.correlationOutboxId());
        assertNull(context.traceId());
    }

    @Test
    void fromNullOutboxEnvelopeIsEmptyContext() {
        MessagingContext context = MessagingContext.fromOutbox(null);

        assertNull(context.correlationOutboxId());
        assertNull(context.traceId());
    }

    @Test
    void fromOutboxEnvelopeCorrelatesOnEnvelopeId() {
        UUID outboxId = UUID.randomUUID();
        OutboxEnvelope envelope = new OutboxEnvelope(outboxId, "SPACE_CREATED", "SPACE", "s-1",
                null, null, "{}", "dedup", Instant.EPOCH);

        MessagingContext context = MessagingContext.fromOutbox(envelope);

        assertEquals(outboxId, context.correlationOutboxId());
        assertNull(context.traceId());
    }

    @Test
    void withTraceIdReturnsNewContextKeepingCorrelation() {
        UUID outboxId = UUID.randomUUID();
        MessagingContext original = MessagingContext.builder().correlationOutboxId(outboxId).build();

        MessagingContext traced = original.withTraceId("trace-123");

        assertNotSame(original, traced);
        assertEquals(outboxId, traced.correlationOutboxId());
        assertEquals("trace-123", traced.traceId());
        assertNull(original.traceId(), "le contexte d'origine reste inchange");
    }

    @Test
    void builderSetsBothFields() {
        UUID outboxId = UUID.randomUUID();

        MessagingContext context = MessagingContext.builder()
                .correlationOutboxId(outboxId)
                .traceId("t")
                .build();

        assertEquals(outboxId, context.correlationOutboxId());
        assertEquals("t", context.traceId());
    }
}
