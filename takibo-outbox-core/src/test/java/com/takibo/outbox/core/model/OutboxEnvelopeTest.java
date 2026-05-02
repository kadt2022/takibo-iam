package com.takibo.outbox.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboxEnvelopeTest {

    @Test
    void rejectsBlankEventType() {
        assertThrows(IllegalArgumentException.class, () ->
                new OutboxEnvelope(UUID.randomUUID(), " ", "A", "1", null, null, "{}", null, Instant.now())
        );
    }
}
