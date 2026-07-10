package com.takibo.managementservice.application.query.result;

import com.takibo.managementservice.domain.model.SpaceStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue résumé applicative d'un space : pas de description, de statusReason ni de
 * version — ces informations d'état appartiennent au détail.
 */
public record SpaceSummaryResult(
        UUID id,
        UUID orgId,
        String code,
        String name,
        SpaceStatus status,
        UUID ownerAccountId,
        Instant createdAt,
        Instant updatedAt
) {}
