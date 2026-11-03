package com.takibo.managementservice.infrastructure.outbox;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SpaceCreatedOutboxHandlerTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID OWNER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final SpaceProvisioningPort provisioning = mock(SpaceProvisioningPort.class);
    private final SpaceCreatedOutboxHandler handler = new SpaceCreatedOutboxHandler(json, provisioning);

    @Test
    void handlesOnlySpaceCreatedEvents() {
        assertThat(handler.eventType()).isEqualTo("SPACE_CREATED");
    }

    @Test
    void consumesExactlyWhatThePublisherWrites() throws Exception {
        OutboxPublisher outbox = mock(OutboxPublisher.class);
        SpaceCreatedEvent event = new SpaceCreatedEvent(
                UUID.randomUUID(), ORG_ID, SPACE_ID, OWNER_ID,
                ActorSource.SERVICE_ACCOUNT, Instant.parse("2030-01-01T00:00:00Z")
        );
        new OutboxSpaceEventPublisher(outbox, json).publish(event);
        ArgumentCaptor<OutboxEnvelope> written = ArgumentCaptor.forClass(OutboxEnvelope.class);
        verify(outbox).publish(written.capture());

        handler.handle(written.getValue());

        verify(provisioning).onSpaceCreated(
                ORG_ID, SPACE_ID, OWNER_ID, "SERVICE_ACCOUNT", written.getValue().id()
        );
    }

    @Test
    void anEventWithoutActorIsAttributedToTheSystem() throws Exception {
        OutboxEnvelope envelope = envelope(
                "{\"orgId\":\"" + ORG_ID + "\",\"spaceId\":\"" + SPACE_ID
                        + "\",\"ownerAccountId\":\"" + OWNER_ID + "\",\"actorSource\":null}"
        );

        handler.handle(envelope);

        verify(provisioning).onSpaceCreated(ORG_ID, SPACE_ID, OWNER_ID, "SYSTEM", envelope.id());
    }

    @Test
    void aMalformedPayloadFailsTheDeliverySoTheOutboxCanRetryIt() {
        OutboxEnvelope envelope = envelope("{\"orgId\":\"not-a-uuid\"}");

        assertThatThrownBy(() -> handler.handle(envelope)).isInstanceOf(Exception.class);
        verifyNoInteractions(provisioning);
    }

    @Test
    void anUnknownActorSourceIsRefused() {
        OutboxEnvelope envelope = envelope("{\"spaceId\":\"" + SPACE_ID + "\",\"actorSource\":\"ROOT\"}");

        assertThatThrownBy(() -> handler.handle(envelope)).isInstanceOf(Exception.class);
        verifyNoInteractions(provisioning);
    }

    private static OutboxEnvelope envelope(String payload) {
        return OutboxEnvelope.of("SPACE_CREATED", "SPACE", SPACE_ID.toString(), ORG_ID, SPACE_ID, payload,
                "SPACE:CREATED:" + SPACE_ID);
    }
}
