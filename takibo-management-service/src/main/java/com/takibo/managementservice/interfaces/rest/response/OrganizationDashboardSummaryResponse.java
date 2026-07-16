package com.takibo.managementservice.interfaces.rest.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Résumé du tableau de bord d'une organisation (récit Dashboard 01).
 * {@code usersTotal} / {@code activeUsersTotal} comptent des comptes DISTINCTS
 * (un account dans plusieurs Spaces = un seul utilisateur).
 */
public record OrganizationDashboardSummaryResponse(
        UUID organizationId,
        long usersTotal,
        long activeUsersTotal,
        long spacesTotal,
        Instant generatedAt
) {}
