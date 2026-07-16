package com.takibo.managementservice.interfaces.rest.api;

import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
import com.takibo.managementservice.application.dashboard.OrgDashboardSummaryService;
import com.takibo.managementservice.application.dashboard.OrganizationDashboardSummaryResult;
import com.takibo.managementservice.interfaces.rest.response.OrganizationDashboardSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-side du tableau de bord organisationnel (récit Dashboard 01).
 * Identification par UUID {@code orgId} (machine-first, doctrine TMS) — jamais l'orgCode.
 * <p>
 * L'autorisation (sujet HUMAN, portée ORGANIZATION, org du token == orgId du chemin,
 * R_ORG_OWNER/R_ORG_ADMIN) est garantie par le PolicyEvaluator. Le contrôleur ajoute
 * une défense en profondeur : le token doit être situé dans CETTE organisation.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/dashboard")
@RequiredArgsConstructor
public class OrgDashboardController {

    private final OrgDashboardSummaryService service;
    private final CurrentOrganizationContextCase currentOrganizationContext;

    @GetMapping("/summary")
    public ResponseEntity<OrganizationDashboardSummaryResponse> summary(
            @PathVariable("orgId") UUID orgId) {

        UUID currentOrg = currentOrganizationContext.requireCurrentOrganizationId();
        if (!orgId.equals(currentOrg)) {
            throw new AccessDeniedException("ORG_MISMATCH");
        }

        OrganizationDashboardSummaryResult result = service.summarize(orgId);
        return ResponseEntity.ok(new OrganizationDashboardSummaryResponse(
                result.organizationId(),
                result.usersTotal(),
                result.activeUsersTotal(),
                result.spacesTotal(),
                result.generatedAt()));
    }
}
