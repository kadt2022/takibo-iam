package com.takibo.managementservice.application.dashboard;

import java.time.Instant;
import java.util.UUID;

/**
 * Résultat applicatif du résumé dashboard. La couche application ne dépend pas de
 * l'interface REST : le contrôleur transforme ce Result en réponse HTTP.
 */
public record OrganizationDashboardSummaryResult(
        UUID organizationId,
        long usersTotal,
        long activeUsersTotal,
        long spacesTotal,
        Instant generatedAt
) {}
