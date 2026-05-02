package com.takibo.managementservice.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.managementservice.application.security.ActorSource;
import com.takibo.managementservice.domain.event.SpaceCreatedEvent;
import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.port.OutboxHandler;
import org.springframework.stereotype.Component;

@Component
public class SpaceCreatedOutboxHandler implements OutboxHandler {

    private final ObjectMapper objectMapper;
    private final SpaceProvisioningPort spaceProvisioningPort;

    public SpaceCreatedOutboxHandler(ObjectMapper objectMapper, SpaceProvisioningPort spaceProvisioningPort) {
        this.objectMapper = objectMapper;
        this.spaceProvisioningPort = spaceProvisioningPort;
    }

    @Override
    public String eventType() {
        return "SPACE_CREATED";
    }

    @Override
    public void handle(OutboxEnvelope envelope) throws Exception {
        SpaceCreatedEvent event = objectMapper.readValue(envelope.payloadJson(), SpaceCreatedEvent.class);
        ActorSource actorSource = event.actorSource() != null ? event.actorSource() : ActorSource.SYSTEM;

        spaceProvisioningPort.onSpaceCreated(
                event.orgId(),
                event.spaceId(),
                event.ownerAccountId(),
                actorSource.toString(),
                envelope.id()
        );
    }
}
