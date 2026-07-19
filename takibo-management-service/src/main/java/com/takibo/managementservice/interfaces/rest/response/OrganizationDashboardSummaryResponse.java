package com.takibo.managementservice.interfaces.rest.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Résumé du tableau de bord d'une organisation (récits Dashboard 01 et 02).
 * {@code usersTotal} / {@code activeUsersTotal} comptent des comptes DISTINCTS
 * (un account dans plusieurs Spaces = un seul utilisateur).
 * {@code oauthClientsTotal} compte tous les clients OAuth2 persistés dans les
 * Spaces de l'organisation — jamais aucun secret dans ce contrat.
 */
public record OrganizationDashboardSummaryResponse(
        UUID organizationId,
        long usersTotal,
        long activeUsersTotal,
        long spacesTotal,
        long oauthClientsTotal,
        Instant generatedAt
) {}
