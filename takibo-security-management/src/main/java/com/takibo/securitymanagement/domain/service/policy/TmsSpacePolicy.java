package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.hasPermission;
import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.isSpaceScoped;
import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.isUuid;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.POL_ORG_MISMATCH;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.deny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.permit;

/**
 * /api/v1/orgs/{orgId}/spaces/** (surface TMS, plan de management)
 * Doctrine d'identification : TMS parle en UUID, TIS-CORE en codes lisibles.
 * Le discriminant UUID laisse donc les routes lisibles (codes) à leurs règles dédiées.
 * La surface est FAIL-CLOSED : seules les paires route/action explicitement
 * gouvernées peuvent être permises — collection {READ, CREATE}, détail {READ}.
 * Toute autre action est refusée (POL_SPACE_ACTION_NOT_SUPPORTED), toute
 * sous-route pas encore gouvernée aussi (POL_SPACE_ROUTE_NOT_GOVERNED) :
 * rien de cette surface ne retombe jamais sur POL_DEFAULT_ALLOW.
 */
public final class TmsSpacePolicy implements ResourcePolicy {

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        String path = resource.path();
        // La surface clients OAuth2 (.../spaces/{uuid}/clients[...]) est gouvernée par
        // sa règle dédiée (OAuthClientPolicy, évaluée avant) : elle est retirée de
        // cette surface, sinon la sous-route serait refusée pour tous — admins compris.
        if (OAuthClientRoute.parse(path) != null) {
            return Optional.empty();
        }

        TmsSpaceRoute route = TmsSpaceRoute.parse(path);
        if (route == null) {
            return Optional.empty();
        }

        // Frontière stricte : un token PLATFORM sans org ou un token d'une autre org
        // ne peut pas utiliser cette surface située.
        if (subject.orgId() == null || !subject.orgId().equalsIgnoreCase(route.orgId())) {
            return deny(POL_ORG_MISMATCH,
                    "Token organization does not match the target organization");
        }

        if (route.isSubRoute()) {
            return deny("POL_SPACE_ROUTE_NOT_GOVERNED",
                    "No policy governs this space route yet — denied by default");
        }

        // Plan ORGANIZATION (E3) : l'autorité organisationnelle s'exerce avec un token situé
        // au niveau ORGANIZATION. Un token SPACE ne porte de toute façon aucune P_ORG_*.
        boolean organizationScoped = "ORGANIZATION".equals(subject.scopeLevel());

        if (route.isCollectionRoute()) {
            if (action != Action.READ && action != Action.CREATE) {
                return deny("POL_SPACE_ACTION_NOT_SUPPORTED",
                        "Only READ and CREATE are governed on the spaces collection");
            }
            boolean read = action == Action.READ;
            TechnicalPermission required = read
                    ? TechnicalPermission.ORG_SPACES_READ
                    : TechnicalPermission.ORG_SPACES_CREATE;
            String policyId = read
                    ? "POL_SPACE_LIST_ORG_AUTHORITY_REQUIRED"
                    : "POL_SPACE_CREATE_ORG_AUTHORITY_REQUIRED";
            if (organizationScoped && hasPermission(subject, required)) {
                return permit(policyId, required.code() + " holder may access the spaces collection");
            }
            return deny(policyId, "An ORGANIZATION-scoped token holding " + required.code() + " is required");
        }

        // Détail : seule la lecture est gouvernée — UPDATE/DELETE/lifecycle restent
        // fermés même pour une autorité ORG, jusqu'au récit qui les gouvernera.
        if (action != Action.READ) {
            return deny("POL_SPACE_ACTION_NOT_SUPPORTED",
                    "Only READ is governed on a space detail route");
        }
        if (organizationScoped && hasPermission(subject, TechnicalPermission.ORG_SPACES_READ)) {
            return permit("POL_SPACE_READ_ORG_OR_LOCAL_ADMIN_REQUIRED",
                    "P_ORG_SPACES_READ holder may read the space detail");
        }
        // Exception locale, strictement READ : un token SPACE lit le space qu'il désigne
        // déjà (org ET space du chemin == org ET space du token) avec P_SPACE_READ.
        boolean localSpaceReader = isSpaceScoped(subject)
                && route.spaceId().equalsIgnoreCase(subject.spaceId())
                && hasPermission(subject, TechnicalPermission.SPACE_READ);
        if (localSpaceReader) {
            return permit("POL_SPACE_READ_ORG_OR_LOCAL_ADMIN_REQUIRED",
                    "P_SPACE_READ holder of this space may read its detail");
        }
        return deny("POL_SPACE_READ_ORG_OR_LOCAL_ADMIN_REQUIRED",
                "P_ORG_SPACES_READ (ORGANIZATION token), or P_SPACE_READ for the token's own space");
    }

    /**
     * Route de la surface spaces TMS. Une route est reconnue dès que le chemin est
     * /api/v1/orgs/{UUID}/spaces… — le segment org DOIT être un UUID (les routes
     * TIS-CORE en codes lisibles ne matchent jamais). Trois formes :
     * collection (…/spaces), détail (…/spaces/{UUID}), sous-route (tout le reste).
     */
    record TmsSpaceRoute(String orgId, String spaceId, boolean subRoute) {

        static TmsSpaceRoute parse(String path) {
            // ["", "api", "v1", "orgs", {orgId}, "spaces", ...]
            String[] seg = path.split("/");
            boolean onSurface = seg.length >= 6
                    && seg[0].isEmpty()
                    && "api".equals(seg[1]) && "v1".equals(seg[2])
                    && "orgs".equals(seg[3]) && "spaces".equals(seg[5])
                    && isUuid(seg[4]);
            if (!onSurface) {
                return null;
            }
            if (seg.length == 6) {
                return new TmsSpaceRoute(seg[4], null, false);          // collection
            }
            if (seg.length == 7 && isUuid(seg[6])) {
                return new TmsSpaceRoute(seg[4], seg[6], false);        // détail
            }
            return new TmsSpaceRoute(seg[4], isUuid(seg[6]) ? seg[6] : null, true); // sous-route
        }

        boolean isCollectionRoute() {
            return !subRoute && spaceId == null;
        }

        boolean isDetailRoute() {
            return !subRoute && spaceId != null;
        }

        boolean isSubRoute() {
            return subRoute;
        }
    }
}
