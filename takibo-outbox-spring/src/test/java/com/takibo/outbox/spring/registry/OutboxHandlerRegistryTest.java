package com.takibo.outbox.spring.registry;

import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.port.OutboxHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboxHandlerRegistryTest {

    @Test
    void failsOnDuplicateEventType() {
        OutboxHandler a = new DummyHandler("X");
        OutboxHandler b = new DummyHandler("X");
        assertThrows(IllegalStateException.class, () -> new OutboxHandlerRegistry(List.of(a, b)));
    }

    @Test
    void resolvesHandlerByEventType() {
        OutboxHandler a = new DummyHandler("X");
        OutboxHandlerRegistry registry = new OutboxHandlerRegistry(List.of(a));
        assertNotNull(registry.getOrNull("X"));
    }

    private static class DummyHandler implements OutboxHandler {

        private final String type;

        private DummyHandler(String type) {
            this.type = type;
        }

        @Override
        public String eventType() {
            return type;
        }

        @Override
        public void handle(OutboxEnvelope envelope) {
            UUID id = envelope.id();
        }
    }
}
