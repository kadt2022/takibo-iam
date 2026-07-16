package com.takibo.identitycore.integration.metrics;

import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRepository;
import com.takibo.identitycore.integration.metrics.port.OrganizationUserMetrics;
import com.takibo.identitycore.integration.metrics.port.OrganizationUserMetricsCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implémentation TIS-CORE de la couture {@link OrganizationUserMetricsCase}.
 * <p>
 * S'appuie sur des requêtes COUNT dédiées ({@code count(distinct account_id)}) :
 * aucune liste d'utilisateurs n'est chargée pour compter, aucun fan-out par Space.
 * Le filtre {@code org_id} garantit qu'aucune donnée d'une autre organisation
 * n'est comptée.
 */
@Component
@RequiredArgsConstructor
public class OrganizationUserMetricsAdapter implements OrganizationUserMetricsCase {

    private final JpaUserRepository users;

    @Override
    @Transactional(readOnly = true)
    public OrganizationUserMetrics metricsForOrganization(UUID organizationId) {
        // Une seule requête agrégée : les deux compteurs proviennent du même snapshot.
        JpaUserRepository.OrganizationUserCounts counts =
                users.countOrganizationUsers(organizationId, UserStatus.ACTIVE);
        return new OrganizationUserMetrics(counts.getUsersTotal(), counts.getActiveUsersTotal());
    }
}
