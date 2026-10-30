package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.Decisions.Requirement.require;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.firstUnmet;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.permit;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.hasPermission;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.isOrganizationHuman;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.sameIgnoringCase;

/**
 * /api/v1/orgs/{UUID}/dashboard/** (récit Dashboard 01) : surface FAIL-CLOSED,
 * à l'image de la surface OAuth2. Plan ORGANIZATION : sujet HUMAIN de portée
 * ORGANIZATION dont l'org == org du chemin, et permission P_ORG_READ. Un membre, un
 * token SPACE, un token PLATFORM ou d'une autre org : refusés. Seul /dashboard/summary en
 * READ est gouverné : toute autre sous-route (POL_ORG_DASHBOARD_ROUTE_NOT_GOVERNED) ou action
 * (POL_ORG_DASHBOARD_ACTION_NOT_SUPPORTED) est refusée — rien de cette surface ne retombe
 * jamais sur POL_DEFAULT_ALLOW.
 */
public final class OrgDashboardPolicy implements ResourcePolicy {

    private static final String ADMIN_REQUIRED = "POL_ORG_DASHBOARD_ADMIN_REQUIRED";

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        return DashboardRoute.parse(resource.path())
                .map(route -> firstUnmet(
                        require(() -> isOrganizationHuman(subject) && sameIgnoringCase(subject.orgId(), route.orgId()),
                                "POL_ORG_DASHBOARD_ORG_HUMAN_REQUIRED",
                                "Organization-scoped human token matching the target organization is required"),
                        require(() -> hasPermission(subject, TechnicalPermission.ORG_READ), ADMIN_REQUIRED,
                                "P_ORG_READ required to read the organization dashboard"),
                        require(route::summary, "POL_ORG_DASHBOARD_ROUTE_NOT_GOVERNED",
                                "No policy governs this dashboard route yet — denied by default"),
                        require(() -> action == Action.READ, "POL_ORG_DASHBOARD_ACTION_NOT_SUPPORTED",
                                "Only READ is governed on the organization dashboard summary"))
                        .orElseGet(() -> permit(ADMIN_REQUIRED,
                                "P_ORG_READ holder of the target organization may read the dashboard summary")));
    }

    /**
     * Route de la surface dashboard : /api/v1/orgs/{UUID}/dashboard… Le segment org DOIT être
     * un UUID (doctrine TMS) ; les routes en codes lisibles ne matchent jamais. {@code summary}
     * distingue la seule sous-route gouvernée (…/dashboard/summary) de toutes les autres.
     */
    record DashboardRoute(String orgId, boolean summary) {

        static Optional<DashboardRoute> parse(String path) {
            RequestPath request = RequestPath.of(path);
            return Optional.of(request)
                    .filter(candidate -> candidate.isOrganizationRoute(6)
                            && candidate.segmentIs(5, "dashboard")
                            && candidate.hasUuidOrganization())
                    .map(route -> new DashboardRoute(route.organization(),
                            route.size() == 7 && route.segmentIs(6, "summary")));
        }
    }
}
