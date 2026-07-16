package com.takibo.managementservice.application.dashboard;

import com.takibo.identitycore.integration.metrics.port.OrganizationUserMetrics;
import com.takibo.identitycore.integration.metrics.port.OrganizationUserMetricsCase;
import com.takibo.managementservice.application.query.port.SpaceQueryCase;
import com.takibo.managementservice.interfaces.rest.response.OrganizationDashboardSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * TMS orchestre le résumé du tableau de bord organisationnel :
 * <ul>
 *   <li>{@code spacesTotal} vient de son propre compteur de Spaces ;</li>
 *   <li>les métriques utilisateurs viennent de la couture publique de TIS-CORE
 *       ({@link OrganizationUserMetricsCase}) — jamais des repositories internes.</li>
 * </ul>
 * Uniquement des compteurs : aucune liste chargée, aucun fan-out par Space.
 */
@Service
@RequiredArgsConstructor
public class OrgDashboardSummaryService {

    private final SpaceQueryCase spaceQuery;
    private final OrganizationUserMetricsCase userMetrics;

    @Transactional(readOnly = true)
    public OrganizationDashboardSummaryResponse summarize(UUID organizationId) {
        long spacesTotal = spaceQuery.countSpaces(organizationId);
        OrganizationUserMetrics metrics = userMetrics.metricsForOrganization(organizationId);

        return new OrganizationDashboardSummaryResponse(
                organizationId,
                metrics.usersTotal(),
                metrics.activeUsersTotal(),
                spacesTotal,
                Instant.now());
    }
}
