package com.takibo.outbox.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxEnvelopeTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SPACE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final Instant CREATED = Instant.parse("2026-10-22T10:00:00Z");

    private static OutboxEnvelope envelope(UUID id, String eventType, String aggregateType, String aggregateId,
                                           String payloadJson, Instant createdAt) {
        return new OutboxEnvelope(id, eventType, aggregateType, aggregateId, ORG, SPACE, payloadJson, "dedup-1", createdAt);
    }

    private static void assertRejected(String expectedMessage, Runnable construction) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, construction::run);
        assertEquals(expectedMessage, error.getMessage());
    }

    @Test
    void rejectsBlankEventType() {
        assertThrows(IllegalArgumentException.class, () ->
                new OutboxEnvelope(UUID.randomUUID(), " ", "A", "1", null, null, "{}", null, Instant.now())
        );
    }

    @Test
    void validEnvelope_keepsEveryValue() {
        OutboxEnvelope envelope = envelope(ID, "SPACE_CREATED", "SPACE", "s-1", "{\"k\":1}", CREATED);

        assertEquals(ID, envelope.id());
        assertEquals("SPACE_CREATED", envelope.eventType());
        assertEquals("SPACE", envelope.aggregateType());
        assertEquals("s-1", envelope.aggregateId());
        assertEquals(ORG, envelope.orgId());
        assertEquals(SPACE, envelope.spaceId());
        assertEquals("{\"k\":1}", envelope.payloadJson());
        assertEquals("dedup-1", envelope.dedupKey());
        assertEquals(CREATED, envelope.createdAt());
    }

    @Test
    void idIsRequired() {
        assertRejected("id is required", () -> envelope(null, "E", "A", "1", "{}", CREATED));
    }

    @Test
    void eventTypeIsRequired() {
        assertRejected("eventType is required", () -> envelope(ID, null, "A", "1", "{}", CREATED));
        assertRejected("eventType is required", () -> envelope(ID, "", "A", "1", "{}", CREATED));
    }

    @Test
    void aggregateTypeIsRequired() {
        assertRejected("aggregateType is required", () -> envelope(ID, "E", null, "1", "{}", CREATED));
        assertRejected("aggregateType is required", () -> envelope(ID, "E", "  ", "1", "{}", CREATED));
    }

    @Test
    void aggregateIdIsRequired() {
        assertRejected("aggregateId is required", () -> envelope(ID, "E", "A", null, "{}", CREATED));
        assertRejected("aggregateId is required", () -> envelope(ID, "E", "A", "", "{}", CREATED));
    }

    @Test
    void payloadIsRequired() {
        assertRejected("payloadJson is required", () -> envelope(ID, "E", "A", "1", null, CREATED));
        assertRejected("payloadJson is required", () -> envelope(ID, "E", "A", "1", " ", CREATED));
    }

    @Test
    void optionalTenantAndDedupKeyMayBeAbsent() {
        OutboxEnvelope envelope = new OutboxEnvelope(ID, "E", "A", "1", null, null, "{}", null, CREATED);

        assertNull(envelope.orgId());
        assertNull(envelope.spaceId());
        assertNull(envelope.dedupKey());
    }

    @Test
    void missingCreatedAt_defaultsToNow() {
        Instant before = Instant.now();
        OutboxEnvelope envelope = envelope(ID, "E", "A", "1", "{}", null);

        assertNotNull(envelope.createdAt());
        assertTrue(!envelope.createdAt().isBefore(before) && !envelope.createdAt().isAfter(Instant.now()));
    }

    @Test
    void factory_assignsANewIdAndCreationTime_andValidates() {
        Instant before = Instant.now();
        OutboxEnvelope first = OutboxEnvelope.of("E", "A", "1", ORG, SPACE, "{}", "k");
        OutboxEnvelope second = OutboxEnvelope.of("E", "A", "1", ORG, SPACE, "{}", "k");

        assertNotNull(first.id());
        assertNotEquals(first.id(), second.id());
        assertTrue(!first.createdAt().isBefore(before));
        assertEquals("k", first.dedupKey());
        assertRejected("payloadJson is required", () -> OutboxEnvelope.of("E", "A", "1", null, null, "", null));
    }
}
