package com.takibo.managementservice.interfaces.rest.response;

import com.takibo.managementservice.domain.model.SpaceStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue résumé d'un space pour la liste : pas de description, de statusReason ni de version —
 * ces informations d'état appartiennent au détail.
 */
public record SpaceSummaryResponse(
        UUID id,
        UUID orgId,
        String code,
        String name,
        SpaceStatus status,
        UUID ownerAccountId,
        Instant createdAt,
        Instant updatedAt
) {}
