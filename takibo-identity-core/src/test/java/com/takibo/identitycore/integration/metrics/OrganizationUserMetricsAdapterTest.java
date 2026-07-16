package com.takibo.identitycore.integration.metrics;

import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRepository;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRepository.OrganizationUserCounts;
import com.takibo.identitycore.integration.metrics.port.OrganizationUserMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    private static OrganizationUserCounts counts(long total, long active) {
        OrganizationUserCounts projection = mock(OrganizationUserCounts.class);
        when(projection.getUsersTotal()).thenReturn(total);
        when(projection.getActiveUsersTotal()).thenReturn(active);
        return projection;
    }

    @Test
    void mapsAggregatedProjection_totalAndActive() {
        // 3 comptes distincts (un même account dans plusieurs Spaces ne compte qu'une
        // fois — sémantique du count(distinct account_id)) ; 2 actifs.
        OrganizationUserCounts projection = counts(3L, 2L);
        when(users.countOrganizationUsers(ORG, UserStatus.ACTIVE)).thenReturn(projection);

        OrganizationUserMetrics metrics = adapter.metricsForOrganization(ORG);

        assertThat(metrics.usersTotal()).isEqualTo(3L);
        assertThat(metrics.activeUsersTotal()).isEqualTo(2L);
    }

    @Test
    void singleAggregatedQuery_scopedToGivenOrganization() {
        OrganizationUserCounts projection = counts(5L, 4L);
        when(users.countOrganizationUsers(ORG, UserStatus.ACTIVE)).thenReturn(projection);

        adapter.metricsForOrganization(ORG);

        // Une seule requête, strictement sur l'org donnée — jamais une autre org.
        verify(users).countOrganizationUsers(ORG, UserStatus.ACTIVE);
        verify(users, never()).countOrganizationUsers(OTHER_ORG, UserStatus.ACTIVE);
    }

    @Test
    void countsOnly_neverLoadsAnyUserCollection() {
        OrganizationUserCounts projection = counts(1L, 1L);
        when(users.countOrganizationUsers(ORG, UserStatus.ACTIVE)).thenReturn(projection);

        adapter.metricsForOrganization(ORG);

        // Contrat : on compte, on ne charge jamais de liste pour compter.
        verify(users, never()).findAll();
        verify(users, never()).findAllBySpaceId(any(), any());
    }
}
