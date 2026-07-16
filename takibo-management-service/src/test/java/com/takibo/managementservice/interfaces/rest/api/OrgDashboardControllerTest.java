package com.takibo.managementservice.interfaces.rest.api;

import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
import com.takibo.managementservice.application.dashboard.OrgDashboardSummaryService;
import com.takibo.managementservice.interfaces.rest.response.OrganizationDashboardSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgDashboardControllerTest {

    private static final UUID ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORG = UUID.fromString("dddddddd-0000-0000-0000-000000000009");

    @Mock
    private OrgDashboardSummaryService service;

    @Mock
    private CurrentOrganizationContextCase currentOrganizationContext;

    @InjectMocks
    private OrgDashboardController controller;

    @Test
    void returnsSummary_whenTokenOrganizationMatchesPath() {
        OrganizationDashboardSummaryResponse body =
                new OrganizationDashboardSummaryResponse(ORG, 342L, 128L, 7L, Instant.now());
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG);
        when(service.summarize(ORG)).thenReturn(body);

        ResponseEntity<OrganizationDashboardSummaryResponse> response = controller.summary(ORG);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(body);
    }

    @Test
    void denies_whenTokenOrganizationDiffersFromPath() {
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(OTHER_ORG);

        assertThatThrownBy(() -> controller.summary(ORG))
                .isInstanceOf(AccessDeniedException.class);

        verify(service, never()).summarize(any());
    }
}
