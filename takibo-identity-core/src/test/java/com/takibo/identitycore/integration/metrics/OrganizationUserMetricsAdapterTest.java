package com.takibo.identitycore.integration.metrics;

import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRepository;
import com.takibo.identitycore.integration.metrics.port.OrganizationUserMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationUserMetricsAdapterTest {

    private static final UUID ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORG = UUID.fromString("dddddddd-0000-0000-0000-000000000009");

    @Mock
    private JpaUserRepository users;

    @InjectMocks
    private OrganizationUserMetricsAdapter adapter;

    @Test
    void countsDistinctAccounts_totalAndActive() {
        // 3 comptes distincts (un même account dans plusieurs Spaces ne compte
        // qu'une fois — sémantique portée par count(distinct account_id)) ; 2 actifs.
        when(users.countDistinctAccountsByOrg(ORG)).thenReturn(3L);
        when(users.countDistinctAccountsByOrgAndStatus(ORG, UserStatus.ACTIVE)).thenReturn(2L);

        OrganizationUserMetrics metrics = adapter.metricsForOrganization(ORG);

        assertThat(metrics.usersTotal()).isEqualTo(3L);
        assertThat(metrics.activeUsersTotal()).isEqualTo(2L);
    }

    @Test
    void queriesStrictlyByGivenOrganization_noCrossOrg() {
        when(users.countDistinctAccountsByOrg(ORG)).thenReturn(5L);
        when(users.countDistinctAccountsByOrgAndStatus(ORG, UserStatus.ACTIVE)).thenReturn(4L);

        adapter.metricsForOrganization(ORG);

        verify(users).countDistinctAccountsByOrg(ORG);
        verify(users).countDistinctAccountsByOrgAndStatus(ORG, UserStatus.ACTIVE);
        // Jamais l'org d'un autre tenant.
        verify(users, never()).countDistinctAccountsByOrg(OTHER_ORG);
    }

    @Test
    void countsOnly_neverLoadsAnyUserCollection() {
        when(users.countDistinctAccountsByOrg(ORG)).thenReturn(1L);
        when(users.countDistinctAccountsByOrgAndStatus(ORG, UserStatus.ACTIVE)).thenReturn(1L);

        adapter.metricsForOrganization(ORG);

        // Contrat : on compte, on ne charge jamais de liste pour compter.
        verify(users, never()).findAll();
        verify(users, never()).findAllBySpaceId(any(), any());
    }
}
