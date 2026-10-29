package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.takibo.identitycore.domain.catalogrbac.TechnicalPermission.SPACE_RBAC_ASSIGN;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalPermission.SPACE_RBAC_READ;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalPermission.SPACE_USERS_LIFECYCLE;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalPermission.SPACE_USERS_MANAGE;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalPermission.SPACE_USERS_READ;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.deny;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.permit;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.hasPermission;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.isSpaceScoped;

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
        return Surface.of(resource.path())
                .map(surface -> surface.requiredPermission(action)
                        .map(required -> isSpaceScoped(subject) && hasPermission(subject, required)
                                ? permit(surface.policyId(), required.code() + " granted in the token space")
                                : deny(surface.policyId(),
                                        "A SPACE-scoped token holding " + required.code() + " is required"))
                        .orElseGet(() -> deny("POL_SPACE_ROUTE_NOT_GOVERNED",
                                "No policy governs this space route and action — denied by default")));
    }

    /**
     * Forme d'une route lisible du plan SPACE (/api/v1/orgs/{code}/spaces/{code}/...) et
     * permission exigée par action. Le segment org n'est PAS un UUID (doctrine : TIS-CORE parle
     * en codes, TMS en UUID). Une action absente de la table n'est pas gouvernée.
     */
    enum Surface {
        RBAC_CATALOG("POL_RBAC_READ_ADMIN_REQUIRED", Map.of(Action.READ, SPACE_RBAC_READ)),
        USERS("POL_USER_ADMIN_REQUIRED", Map.of(Action.READ, SPACE_USERS_READ, Action.CREATE, SPACE_USERS_MANAGE)),
        USER("POL_USER_ADMIN_REQUIRED", Map.of(Action.READ, SPACE_USERS_READ, Action.UPDATE, SPACE_USERS_MANAGE)),
        USER_LIFECYCLE("POL_USER_ADMIN_REQUIRED", Map.of(Action.CREATE, SPACE_USERS_LIFECYCLE)),
        USER_ASSIGNMENTS("POL_USER_RBAC_ADMIN_REQUIRED",
                Map.of(Action.READ, SPACE_RBAC_READ, Action.CREATE, SPACE_RBAC_ASSIGN)),
        USER_ASSIGNMENT("POL_USER_RBAC_ADMIN_REQUIRED", Map.of(Action.DELETE, SPACE_RBAC_ASSIGN)),
        UNGOVERNED("POL_SPACE_ROUTE_NOT_GOVERNED", Map.of());

        private static final Set<String> SURFACES = Set.of("users", "roles", "groups", "permissions");
        private static final Set<String> ASSIGNMENTS = Set.of("roles", "groups");
        private static final Map<String, Surface> USER_SUB_RESOURCES = Map.of(
                "suspend", USER_LIFECYCLE, "activate", USER_LIFECYCLE,
                "lock", USER_LIFECYCLE, "deactivate", USER_LIFECYCLE,
                "roles", USER_ASSIGNMENTS, "groups", USER_ASSIGNMENTS);
        private static final int SURFACE = 7;

        private final String policyId;
        private final Map<Action, TechnicalPermission> permissions;

        Surface(String policyId, Map<Action, TechnicalPermission> permissions) {
            this.policyId = policyId;
            this.permissions = permissions.isEmpty() ? Map.of() : new EnumMap<>(permissions);
        }

        static Optional<Surface> of(String path) {
            // ["", "api", "v1", "orgs", {orgCode}, "spaces", {spaceCode}, {surface}, ...]
            return Optional.of(RequestPath.of(path))
                    .filter(request -> request.isOrganizationRoute(SURFACE + 1)
                            && request.segmentIs(5, "spaces")
                            && !request.hasUuidOrganization()
                            && SURFACES.contains(request.segment(SURFACE)))
                    .map(request -> classify(request.from(SURFACE)));
        }

        private static Surface classify(List<String> rest) {
            if (!"users".equals(rest.getFirst())) {
                // Catalogue RBAC du Space (roles, groups, permissions) : lecture seule.
                return rest.size() <= 2 ? RBAC_CATALOG : UNGOVERNED;
            }
            return switch (rest.size()) {
                case 1 -> USERS;
                case 2 -> USER;
                case 3 -> USER_SUB_RESOURCES.getOrDefault(rest.get(2), UNGOVERNED);
                case 4 -> ASSIGNMENTS.contains(rest.get(2)) ? USER_ASSIGNMENT : UNGOVERNED;
                default -> UNGOVERNED;
            };
        }

        Optional<TechnicalPermission> requiredPermission(Action action) {
            return Optional.ofNullable(action).map(permissions::get);
        }

        String policyId() {
            return policyId;
        }
    }
}
