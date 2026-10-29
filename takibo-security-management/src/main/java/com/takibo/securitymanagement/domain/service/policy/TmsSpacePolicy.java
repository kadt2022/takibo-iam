package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.Decisions.Grant.grant;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.ORG_MISMATCH_REASON;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.POL_ORG_MISMATCH;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.Requirement.require;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.deny;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.firstGrant;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.firstUnmet;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.permit;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.hasPermission;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.isOrganizationScoped;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.isSpaceScoped;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.sameIgnoringCase;

/**
 * /api/v1/orgs/{orgId}/spaces/** (surface TMS, plan de management)
 * Doctrine d'identification : TMS parle en UUID, TIS-CORE en codes lisibles.
 * Le discriminant UUID laisse donc les routes lisibles (codes) à leurs règles dédiées.
 * La surface est FAIL-CLOSED : seules les paires route/action explicitement
 * gouvernées peuvent être permises — collection {READ, CREATE}, détail {READ}.
 * Toute autre action est refusée (POL_SPACE_ACTION_NOT_SUPPORTED), toute
 * sous-route pas encore gouvernée aussi (POL_SPACE_ROUTE_NOT_GOVERNED) :
 * rien de cette surface ne retombe jamais sur POL_DEFAULT_ALLOW.
 * Plan ORGANIZATION (E3) : l'autorité organisationnelle s'exerce avec un token situé au
 * niveau ORGANIZATION. Un token SPACE ne porte de toute façon aucune P_ORG_*.
 */
public final class TmsSpacePolicy implements ResourcePolicy {

    private static final String ACTION_NOT_SUPPORTED = "POL_SPACE_ACTION_NOT_SUPPORTED";
    private static final String DETAIL_READ = "POL_SPACE_READ_ORG_OR_LOCAL_ADMIN_REQUIRED";

    /** Accès gouvernés sur la collection : permission ORG exigée et policyId par action. */
    private static final Map<Action, CollectionAccess> COLLECTION_ACCESS = new EnumMap<>(Map.of(
            Action.READ, new CollectionAccess(TechnicalPermission.ORG_SPACES_READ,
                    "POL_SPACE_LIST_ORG_AUTHORITY_REQUIRED"),
            Action.CREATE, new CollectionAccess(TechnicalPermission.ORG_SPACES_CREATE,
                    "POL_SPACE_CREATE_ORG_AUTHORITY_REQUIRED")));

    private record CollectionAccess(TechnicalPermission permission, String policyId) {
    }

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        String path = resource.path();
        // La surface clients OAuth2 (.../spaces/{uuid}/clients[...]) est gouvernée par sa règle
        // dédiée : elle est retirée de cette surface, sinon la sous-route serait refusée pour
        // tous — admins compris.
        return OAuthClientRoute.parse(path).isPresent()
                ? Optional.empty()
                : SpaceRoute.parse(path).map(route -> decide(subject, route, action));
    }

    private static PolicyDecision decide(Subject subject, SpaceRoute route, Action action) {
        // Frontière stricte : un token PLATFORM sans org ou un token d'une autre org ne peut
        // pas utiliser cette surface située.
        return firstUnmet(
                require(() -> sameIgnoringCase(subject.orgId(), route.orgId()), POL_ORG_MISMATCH,
                        ORG_MISMATCH_REASON),
                require(() -> route.kind() != SpaceRoute.Kind.SUB_ROUTE, "POL_SPACE_ROUTE_NOT_GOVERNED",
                        "No policy governs this space route yet — denied by default"))
                .orElseGet(() -> route.kind() == SpaceRoute.Kind.COLLECTION
                        ? collection(subject, action)
                        : detail(subject, route, action));
    }

    private static PolicyDecision collection(Subject subject, Action action) {
        return Optional.ofNullable(COLLECTION_ACCESS.get(action))
                .map(access -> isOrganizationScoped(subject) && hasPermission(subject, access.permission())
                        ? permit(access.policyId(), access.permission().code() + " holder may access the spaces collection")
                        : deny(access.policyId(),
                                "An ORGANIZATION-scoped token holding " + access.permission().code() + " is required"))
                .orElseGet(() -> deny(ACTION_NOT_SUPPORTED, "Only READ and CREATE are governed on the spaces collection"));
    }

    /**
     * Détail : seule la lecture est gouvernée — UPDATE/DELETE/lifecycle restent fermés même pour
     * une autorité ORG. Exception locale, strictement READ : un token SPACE lit le space qu'il
     * désigne déjà (org ET space du chemin == org ET space du token) avec P_SPACE_READ.
     */
    private static PolicyDecision detail(Subject subject, SpaceRoute route, Action action) {
        return firstUnmet(
                require(() -> action == Action.READ, ACTION_NOT_SUPPORTED, "Only READ is governed on a space detail route"))
                .or(() -> firstGrant(DETAIL_READ,
                        grant(() -> isOrganizationScoped(subject)
                                        && hasPermission(subject, TechnicalPermission.ORG_SPACES_READ),
                                "P_ORG_SPACES_READ holder may read the space detail"),
                        grant(() -> isSpaceScoped(subject)
                                        && route.spaceId().equalsIgnoreCase(subject.spaceId())
                                        && hasPermission(subject, TechnicalPermission.SPACE_READ),
                                "P_SPACE_READ holder of this space may read its detail")))
                .orElseGet(() -> deny(DETAIL_READ,
                        "P_ORG_SPACES_READ (ORGANIZATION token), or P_SPACE_READ for the token's own space"));
    }

    /**
     * Route de la surface spaces TMS : /api/v1/orgs/{UUID}/spaces… — le segment org DOIT être
     * un UUID (les routes TIS-CORE en codes lisibles ne matchent jamais). Trois formes :
     * collection (…/spaces), détail (…/spaces/{UUID}), sous-route (tout le reste).
     */
    record SpaceRoute(String orgId, String spaceId, Kind kind) {

        enum Kind { COLLECTION, DETAIL, SUB_ROUTE }

        private static final int SPACES = 5;
        private static final int SPACE = 6;

        static Optional<SpaceRoute> parse(String path) {
            // ["", "api", "v1", "orgs", {orgId}, "spaces", ...]
            return Optional.of(RequestPath.of(path))
                    .filter(request -> request.isOrganizationRoute(SPACES + 1)
                            && request.segmentIs(SPACES, "spaces")
                            && request.hasUuidOrganization())
                    .map(SpaceRoute::of);
        }

        private static SpaceRoute of(RequestPath request) {
            if (request.size() == SPACES + 1) {
                return new SpaceRoute(request.organization(), null, Kind.COLLECTION);
            }
            String spaceId = request.isUuidAt(SPACE) ? request.segment(SPACE) : null;
            Kind kind = request.size() == SPACE + 1 && spaceId != null ? Kind.DETAIL : Kind.SUB_ROUTE;
            return new SpaceRoute(request.organization(), spaceId, kind);
        }
    }
}
