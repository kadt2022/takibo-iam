package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.hasPermission;
import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.isSpaceScoped;
import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.isUuid;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.deny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.permit;

/**
 * /api/v1/orgs/{orgCode}/spaces/{spaceCode}/{users|roles|groups|permissions}[...]
 * (routes lisibles, plan SPACE). Chaque paire route/action exige sa permission P_SPACE_*
 * et un token situé dans un Space : la correspondance du Space désigné par les codes reste
 * vérifiée par SpaceBoundaryGuard, qui seul résout ces codes. Paire non gouvernée, y compris
 * une méthode sans endpoint : 403 (POL_SPACE_ROUTE_NOT_GOVERNED), jamais 405, comme sur les
 * surfaces UUID — la réponse ne révèle pas quelles méthodes existent.
 */
public final class ReadableSpacePolicy implements ResourcePolicy {

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        ReadableSpaceRoute route = ReadableSpaceRoute.parse(resource.path());
        if (route == null) {
            return Optional.empty();
        }
        TechnicalPermission required = route.requiredPermission(action);
        if (required == null) {
            return deny("POL_SPACE_ROUTE_NOT_GOVERNED",
                    "No policy governs this space route and action — denied by default");
        }
        if (!isSpaceScoped(subject) || !hasPermission(subject, required)) {
            return deny(route.policyId(),
                    "A SPACE-scoped token holding " + required.code() + " is required");
        }
        return permit(route.policyId(), required.code() + " granted in the token space");
    }

    /**
     * Route lisible du plan SPACE : /api/v1/orgs/{code}/spaces/{code}/... Le segment org
     * n'est PAS un UUID (doctrine : TIS-CORE parle en codes, TMS en UUID).
     */
    record ReadableSpaceRoute(List<String> rest) {

        private static final Set<String> LIFECYCLE = Set.of("suspend", "activate", "lock", "deactivate");

        static ReadableSpaceRoute parse(String path) {
            // ["", "api", "v1", "orgs", {orgCode}, "spaces", {spaceCode}, {surface}, ...]
            String[] seg = path.split("/");
            boolean onSurface = seg.length >= 8
                    && seg[0].isEmpty()
                    && "api".equals(seg[1]) && "v1".equals(seg[2])
                    && "orgs".equals(seg[3]) && "spaces".equals(seg[5])
                    && !isUuid(seg[4])
                    && Set.of("users", "roles", "groups", "permissions").contains(seg[7]);
            return onSurface ? new ReadableSpaceRoute(List.of(seg).subList(7, seg.length)) : null;
        }

        String policyId() {
            if (!"users".equals(rest.getFirst())) {
                return "POL_RBAC_READ_ADMIN_REQUIRED";
            }
            return rest.size() >= 3 && ("roles".equals(rest.get(2)) || "groups".equals(rest.get(2)))
                    ? "POL_USER_RBAC_ADMIN_REQUIRED"
                    : "POL_USER_ADMIN_REQUIRED";
        }

        TechnicalPermission requiredPermission(Action action) {
            if (!"users".equals(rest.getFirst())) {
                // Catalogue RBAC du Space : lecture seule.
                return rest.size() <= 2 && action == Action.READ ? TechnicalPermission.SPACE_RBAC_READ : null;
            }
            return switch (rest.size()) {
                case 1 -> action == Action.READ ? TechnicalPermission.SPACE_USERS_READ
                        : action == Action.CREATE ? TechnicalPermission.SPACE_USERS_MANAGE : null;
                case 2 -> action == Action.READ ? TechnicalPermission.SPACE_USERS_READ
                        : action == Action.UPDATE ? TechnicalPermission.SPACE_USERS_MANAGE : null;
                case 3 -> {
                    if (LIFECYCLE.contains(rest.get(2))) {
                        yield action == Action.CREATE ? TechnicalPermission.SPACE_USERS_LIFECYCLE : null;
                    }
                    if ("roles".equals(rest.get(2)) || "groups".equals(rest.get(2))) {
                        yield action == Action.READ ? TechnicalPermission.SPACE_RBAC_READ
                                : action == Action.CREATE ? TechnicalPermission.SPACE_RBAC_ASSIGN : null;
                    }
                    yield null;
                }
                case 4 -> ("roles".equals(rest.get(2)) || "groups".equals(rest.get(2))) && action == Action.DELETE
                        ? TechnicalPermission.SPACE_RBAC_ASSIGN : null;
                default -> null;
            };
        }
    }
}
