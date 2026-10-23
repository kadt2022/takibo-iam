package com.takibo.outbox.spring.registry;

import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.port.OutboxHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxHandlerRegistryBehaviorTest {

    private static OutboxHandler handler(String type) {
        return new OutboxHandler() {
            @Override public String eventType() { return type; }
            @Override public void handle(OutboxEnvelope envelope) { }
        };
    }

    @Test
    void eventTypesAreTrimmed_onRegistrationAndLookup() {
        OutboxHandler handler = handler(" SPACE_CREATED ");
        OutboxHandlerRegistry registry = new OutboxHandlerRegistry(List.of(handler));

        assertThat(registry.getOrNull("SPACE_CREATED")).isSameAs(handler);
        assertThat(registry.getOrNull("  SPACE_CREATED")).isSameAs(handler);
    }

    @Test
    void lookupIsCaseSensitive_andUnknownOrNullTypesHaveNoHandler() {
        OutboxHandlerRegistry registry = new OutboxHandlerRegistry(List.of(handler("SPACE_CREATED")));

        assertThat(registry.getOrNull("space_created")).isNull();
        assertThat(registry.getOrNull("OTHER")).isNull();
        assertThat(registry.getOrNull(null)).isNull();
    }

    @Test
    void duplicatesAreDetectedAfterTrimming() {
        assertThatThrownBy(() -> new OutboxHandlerRegistry(List.of(handler("X"), handler(" X "))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate OutboxHandler for eventType: X");
    }

    @Test
    void emptyRegistry_resolvesNothing_andNullTypeHandlerIsRegisteredUnderEmptyKey() {
        assertThat(new OutboxHandlerRegistry(List.of()).getOrNull("X")).isNull();

        OutboxHandler nullType = handler(null);
        assertThat(new OutboxHandlerRegistry(List.of(nullType)).getOrNull(null)).isSameAs(nullType);
    }
}
