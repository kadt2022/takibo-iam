package com.takibo.managementservice.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.managementservice.domain.event.SpaceCreatedEvent;
import com.takibo.managementservice.domain.model.ActorSource;
import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.port.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxSpaceEventPublisherTest {

    static final SpaceCreatedEvent EVENT = new SpaceCreatedEvent(
            UUID.fromString("eeeeeeee-0000-0000-0000-000000000005"),
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"),
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"),
            UUID.fromString("cccccccc-0000-0000-0000-000000000003"),
            ActorSource.HUMAN,
            Instant.parse("2030-01-01T00:00:00Z")
    );

    private final OutboxPublisher outbox = mock(OutboxPublisher.class);

    @Test
    void publishesTheSpaceCreationInsideItsOrganizationAndSpaceBoundary() throws Exception {
        ObjectMapper json = mock(ObjectMapper.class);
        when(json.writeValueAsString(EVENT)).thenReturn("{\"payload\":true}");

        new OutboxSpaceEventPublisher(outbox, json).publish(EVENT);

        OutboxEnvelope envelope = captured();
        assertThat(envelope.id()).isNotNull();
        assertThat(envelope.eventType()).isEqualTo("SPACE_CREATED");
        assertThat(envelope.aggregateType()).isEqualTo("SPACE");
        assertThat(envelope.aggregateId()).isEqualTo(EVENT.spaceId().toString());
        assertThat(envelope.orgId()).isEqualTo(EVENT.orgId());
        assertThat(envelope.spaceId()).isEqualTo(EVENT.spaceId());
        assertThat(envelope.payloadJson()).isEqualTo("{\"payload\":true}");
        assertThat(envelope.createdAt()).isNotNull();
    }

    @Test
    void theDeduplicationKeyIsStableForAGivenSpace() throws Exception {
        ObjectMapper json = mock(ObjectMapper.class);
        when(json.writeValueAsString(any())).thenReturn("{}");
        OutboxSpaceEventPublisher publisher = new OutboxSpaceEventPublisher(outbox, json);

        publisher.publish(EVENT);
        publisher.publish(EVENT);

        ArgumentCaptor<OutboxEnvelope> envelopes = ArgumentCaptor.forClass(OutboxEnvelope.class);
        verify(outbox, times(2)).publish(envelopes.capture());
        assertThat(envelopes.getAllValues())
                .extracting(OutboxEnvelope::dedupKey)
                .containsOnly("SPACE:CREATED:" + EVENT.spaceId());
        assertThat(envelopes.getAllValues())
                .extracting(OutboxEnvelope::id)
                .doesNotHaveDuplicates();
    }

    @Test
    void aSerializationFailureAbortsThePublicationInsteadOfWritingAnEmptyEvent() throws Exception {
        ObjectMapper json = mock(ObjectMapper.class);
        when(json.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("boom") { });

        OutboxSpaceEventPublisher publisher = new OutboxSpaceEventPublisher(outbox, json);

        assertThatThrownBy(() -> publisher.publish(EVENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to serialize outbox payload")
                .hasCauseInstanceOf(JsonProcessingException.class);
        verify(outbox, never()).publish(any());
    }

    private OutboxEnvelope captured() {
        ArgumentCaptor<OutboxEnvelope> envelope = ArgumentCaptor.forClass(OutboxEnvelope.class);
        verify(outbox).publish(envelope.capture());
        return envelope.getValue();
    }
}
