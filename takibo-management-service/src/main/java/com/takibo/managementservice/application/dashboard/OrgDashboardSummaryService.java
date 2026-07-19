package com.takibo.managementservice.application.dashboard;

import com.takibo.identitycore.integration.metrics.port.OrganizationUserMetrics;
import com.takibo.identitycore.integration.metrics.port.OrganizationUserMetricsCase;
import com.takibo.managementservice.application.query.port.OAuthClientQueryCase;
import com.takibo.managementservice.application.query.port.SpaceQueryCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * TMS orchestre le résumé du tableau de bord organisationnel :
 * <ul>
 *   <li>{@code spacesTotal} et {@code oauthClientsTotal} viennent de ses propres
 *       compteurs (Spaces et clients OAuth2 sont persistés côté TMS) ;</li>
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
    private final OAuthClientQueryCase clientQuery;

    @Transactional(readOnly = true)
    public OrganizationDashboardSummaryResult summarize(UUID organizationId) {
        long spacesTotal = spaceQuery.countSpaces(organizationId);
        long oauthClientsTotal = clientQuery.countClients(organizationId);
        OrganizationUserMetrics metrics = userMetrics.metricsForOrganization(organizationId);

        return new OrganizationDashboardSummaryResult(
                organizationId,
                metrics.usersTotal(),
                metrics.activeUsersTotal(),
                spacesTotal,
                oauthClientsTotal,
                Instant.now());
    }
}
