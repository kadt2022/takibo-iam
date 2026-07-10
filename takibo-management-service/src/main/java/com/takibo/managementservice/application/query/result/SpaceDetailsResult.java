package com.takibo.managementservice.application.query.result;

import com.takibo.managementservice.domain.model.SpaceStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Détail applicatif d'un space. statusReason est la raison persistante du statut
 * courant (doctrine Space : la frontière opérationnelle porte son état).
 */
public record SpaceDetailsResult(
        UUID id,
        UUID orgId,
        String code,
        String name,
        String description,
        SpaceStatus status,
        String statusReason,
        Instant statusUpdatedAt,
        UUID ownerAccountId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {}
