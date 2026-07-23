package com.takibo.managementservice.domain.event;

import com.takibo.managementservice.domain.model.ActorSource;

import java.time.Instant;
import java.util.UUID;

public record SpaceCreatedEvent(
    UUID eventId,
    UUID orgId,
    UUID spaceId,
    UUID ownerAccountId,
    ActorSource actorSource,
    Instant occurredAt
) {}
