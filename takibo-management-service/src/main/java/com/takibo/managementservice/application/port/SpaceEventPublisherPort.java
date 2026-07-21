package com.takibo.managementservice.application.port;

import com.takibo.managementservice.domain.event.SpaceCreatedEvent;

/**
 * Frontière de publication des événements de space. La couche application émet
 * un événement de domaine ; l'infrastructure décide de l'enveloppe outbox, de la
 * clé de déduplication et de la sérialisation JSON.
 */
public interface SpaceEventPublisherPort {

    void publish(SpaceCreatedEvent event);
}
