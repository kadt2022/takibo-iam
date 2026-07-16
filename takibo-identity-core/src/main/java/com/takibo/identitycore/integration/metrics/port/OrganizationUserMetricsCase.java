package com.takibo.identitycore.integration.metrics.port;

import java.util.UUID;

/**
 * Couture publique de métriques exposée par TIS-CORE aux plans qui orchestrent
 * un résumé (TMS). TMS appelle cette interface — il ne dépend jamais des
 * repositories internes de TIS-CORE.
 * <p>
 * Frontière : toutes les métriques sont strictement scopées à {@code organizationId}
 * (aucune donnée d'une autre organisation).
 */
public interface OrganizationUserMetricsCase {

    OrganizationUserMetrics metricsForOrganization(UUID organizationId);
}
