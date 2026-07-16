package com.takibo.identitycore.integration.metrics.port;

/**
 * Métriques utilisateurs agrégées d'une organisation, comptées côté TIS-CORE.
 * <p>
 * {@code usersTotal} et {@code activeUsersTotal} comptent des <b>comptes distincts</b>
 * (un même {@code account} présent dans plusieurs Spaces d'une organisation ne compte
 * qu'une fois). Ce sont des compteurs — aucune collection d'utilisateurs n'est chargée.
 */
public record OrganizationUserMetrics(long usersTotal, long activeUsersTotal) {}
