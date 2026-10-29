package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.hasPermission;
import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.isUuid;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.deny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.permit;

/**
 * /api/v1/orgs/{UUID}/dashboard/** (récit Dashboard 01) : surface FAIL-CLOSED,
 * à l'image de la surface OAuth2. Plan ORGANIZATION : sujet HUMAIN de portée
 * ORGANIZATION dont l'org == org du chemin, et permission P_ORG_READ. Un membre, un
 * token SPACE, un token PLATFORM ou d'une autre org : refusés. Seul /dashboard/summary en READ est gouverné : toute autre sous-route
 * (POL_ORG_DASHBOARD_ROUTE_NOT_GOVERNED) ou action (POL_ORG_DASHBOARD_ACTION_NOT_SUPPORTED)
 * est refusée — rien de cette surface ne retombe jamais sur POL_DEFAULT_ALLOW.
 */
public final class OrgDashboardPolicy implements ResourcePolicy {

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        OrgDashboardRoute route = OrgDashboardRoute.parse(resource.path());
        if (route == null) {
            return Optional.empty();
        }

        if (!Subject.TYPE_HUMAN.equals(subject.subjectType())
                || !"ORGANIZATION".equals(subject.scopeLevel())
                || subject.orgId() == null
                || !subject.orgId().equalsIgnoreCase(route.orgId())) {
            return deny("POL_ORG_DASHBOARD_ORG_HUMAN_REQUIRED",
                    "Organization-scoped human token matching the target organization is required");
        }
        if (!hasPermission(subject, TechnicalPermission.ORG_READ)) {
            return deny("POL_ORG_DASHBOARD_ADMIN_REQUIRED",
                    "P_ORG_READ required to read the organization dashboard");
        }
        if (!route.isSummary()) {
            return deny("POL_ORG_DASHBOARD_ROUTE_NOT_GOVERNED",
                    "No policy governs this dashboard route yet — denied by default");
        }
        if (action != Action.READ) {
            return deny("POL_ORG_DASHBOARD_ACTION_NOT_SUPPORTED",
                    "Only READ is governed on the organization dashboard summary");
        }
        return permit("POL_ORG_DASHBOARD_ADMIN_REQUIRED",
                "P_ORG_READ holder of the target organization may read the dashboard summary");
    }

    /**
     * Route de la surface dashboard : /api/v1/orgs/{UUID}/dashboard… Le segment org
     * DOIT être un UUID (doctrine TMS) ; les routes en codes lisibles ne matchent jamais.
     * {@link #isSummary()} distingue la seule sous-route gouvernée (…/dashboard/summary)
     * de toutes les autres, refusées par défaut.
     */
    record OrgDashboardRoute(String orgId, boolean summary) {
        static OrgDashboardRoute parse(String path) {
            // ["", "api", "v1", "orgs", {orgId}, "dashboard", ...]
            String[] seg = path.split("/");
            boolean onSurface = seg.length >= 6
                    && seg[0].isEmpty()
                    && "api".equals(seg[1]) && "v1".equals(seg[2])
                    && "orgs".equals(seg[3]) && "dashboard".equals(seg[5])
                    && isUuid(seg[4]);
            if (!onSurface) {
                return null;
            }
            boolean isSummary = seg.length == 7 && "summary".equals(seg[6]);
            return new OrgDashboardRoute(seg[4], isSummary);
        }

        boolean isSummary() {
            return summary;
        }
    }
}
