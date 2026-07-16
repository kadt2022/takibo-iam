package com.takibo.managementservice.application.dashboard;

import com.takibo.identitycore.integration.metrics.port.OrganizationUserMetrics;
import com.takibo.identitycore.integration.metrics.port.OrganizationUserMetricsCase;
import com.takibo.managementservice.application.query.port.SpaceQueryCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgDashboardSummaryServiceTest {

    private static final UUID ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Mock
    private SpaceQueryCase spaceQuery;

    @Mock
    private OrganizationUserMetricsCase userMetrics;

    @InjectMocks
    private OrgDashboardSummaryService service;

    @Test
    void assemblesRealCountsFromSpaceAndUserSeams() {
        when(spaceQuery.countSpaces(ORG)).thenReturn(7L);
        when(userMetrics.metricsForOrganization(ORG)).thenReturn(new OrganizationUserMetrics(342L, 128L));

        OrganizationDashboardSummaryResult summary = service.summarize(ORG);

        assertThat(summary.organizationId()).isEqualTo(ORG);
        assertThat(summary.usersTotal()).isEqualTo(342L);
        assertThat(summary.activeUsersTotal()).isEqualTo(128L);
        assertThat(summary.spacesTotal()).isEqualTo(7L);
        assertThat(summary.generatedAt()).isNotNull();
    }

    @Test
    void usesDedicatedCounters_neverListsSpaces() {
        when(spaceQuery.countSpaces(ORG)).thenReturn(0L);
        when(userMetrics.metricsForOrganization(ORG)).thenReturn(new OrganizationUserMetrics(0L, 0L));

        service.summarize(ORG);

        verify(spaceQuery).countSpaces(ORG);
        verify(spaceQuery, never()).listSpaces(any(), any(), anyString(), anyInt(), anyInt(), anyString());
    }
}
