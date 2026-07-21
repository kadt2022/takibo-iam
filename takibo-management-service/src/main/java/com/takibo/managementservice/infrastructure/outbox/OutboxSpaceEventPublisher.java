package com.takibo.managementservice.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.managementservice.application.port.SpaceEventPublisherPort;
import com.takibo.managementservice.domain.event.SpaceCreatedEvent;
import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.port.OutboxPublisher;
import org.springframework.stereotype.Component;

/**
 * Adaptateur outbox : traduit un SpaceCreatedEvent de domaine en enveloppe
 * transactionnelle et prend en charge la sérialisation JSON. La couche
 * application ne connaît ni Jackson ni le modèle technique de l'outbox.
 */
@Component
public class OutboxSpaceEventPublisher implements SpaceEventPublisherPort {

    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;

    public OutboxSpaceEventPublisher(OutboxPublisher outboxPublisher, ObjectMapper objectMapper) {
        this.outboxPublisher = outboxPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(SpaceCreatedEvent event) {
        outboxPublisher.publish(
                OutboxEnvelope.of(
                        "SPACE_CREATED",
                        "SPACE",
                        event.spaceId().toString(),
                        event.orgId(),
                        event.spaceId(),
                        toJson(event),
                        "SPACE:CREATED:" + event.spaceId()
                )
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize outbox payload", e);
        }
    }
}
