package com.takibo.securitymanagement.domain.service;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.domain.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class PolicyEvaluator {

    private static final String POL_ORG_MISMATCH = "POL_ORG_MISMATCH";

    public PolicyDecision evaluate(Subject subject,
                                   Resource resource,
                                   Action action,
                                   Environment env) {

        // 1️⃣ Isolation tenant (v1 : on laisse l'espace pour plus tard si besoin)
        PolicyDecision isolation = evaluateTenantIsolation(subject, resource);
        if (isolation.isDeny()) {
            return isolation;
        }

        // 2️⃣ Politiques spécifiques aux ressources
        PolicyDecision resourcePolicy = evaluateResourcePolicies(subject, resource, action);
        if (resourcePolicy.isDeny() || isExplicitPermit(resourcePolicy)) {
            return resourcePolicy;
        }

        // 3️⃣ Par défaut : PERMIT si rien ne bloque
        return PolicyDecision.builder()
                .effect(Effect.PERMIT)
                .policyId("POL_DEFAULT_ALLOW")
                .reason("No denying policy matched")
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Isolation tenant (simple org-level pour v1)
    // Contrat RBAC-06 : une ressource qui désigne une organisation exige
    // token.org_id == orgId. Un token sans organisation (PLATFORM) est refusé tant que
    // l'intervention plateforme n'est pas explicitement située (RBAC-08).
    // ─────────────────────────────────────────────────────────────
    private PolicyDecision evaluateTenantIsolation(Subject subject, Resource resource) {

        if (resource.orgId() != null && !sameUuid(resource.orgId(), subject.orgId())) {

            log.warn("Tenant isolation violation: subject.orgId={}, resource.orgId={}",
                    subject.orgId(), resource.orgId());

            return PolicyDecision.builder()
                    .effect(Effect.DENY)
                    .policyId(POL_ORG_MISMATCH)
                    .reason("User does not belong to target organization")
                    .build();
        }

        // Pas de violation
        return PolicyDecision.builder()
                .effect(Effect.PERMIT)
                .policyId("POL_TENANT_ISOLATION_OK")
                .reason("Tenant isolation OK")
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Règles spécifiques aux resources (RBAC + ABAC simple)
    // Chaque surface a sa règle dédiée ; la première qui DENY gagne.
    // NB : l'inscription POST /api/v1/orgs/signup est gouvernée plus haut (authentification requise).
    // ─────────────────────────────────────────────────────────────
    private PolicyDecision evaluateResourcePolicies(Subject subject,
                                                   Resource resource,
                                                   Action action) {

        String path = resource.path();

        // ORDONNANCEMENT : la règle clients OAuth2 DOIT précéder denyTmsSpaceSurface —
        // TmsSpaceRoute.parse classe .../spaces/{uuid}/clients comme sous-route et la
        // refuserait pour tous (POL_SPACE_ROUTE_NOT_GOVERNED), admins légitimes compris.
        return organizationSignupPolicy(path, action)
                .or(() -> currentUserSpacesPolicy(subject, path, action))
                .or(() -> denyOrgDashboardSurface(subject, path, action))
                .or(() -> readableSpaceSurface(subject, path, action))
                .or(() -> denyOAuthClientSurface(subject, path, action))
                .or(() -> denyTmsSpaceSurface(subject, path, action))
                .or(() -> denyUngovernedTmsSurface(path))
                .orElseGet(() -> PolicyDecision.builder()
                        .effect(Effect.PERMIT)
                        .policyId("POL_RESOURCE_ALLOW")
                        .reason("No specific resource policy denied access")
                        .build());
    }

    private static boolean isExplicitPermit(PolicyDecision decision) {
        return !decision.isDeny() && !"POL_RESOURCE_ALLOW".equals(decision.getPolicyId());
    }

    /**
     * RBAC-05/06 : une décision lit une permission située du token, jamais un nom de rôle.
     * Les permissions sont calculées à l'émission pour la frontière du token (projection
     * ORGANIZATION -> SPACE seulement) : un token SPACE ne porte jamais de P_ORG_*.
     */
    private static boolean hasPermission(Subject subject, TechnicalPermission permission) {
        return subject.permissions() != null && subject.permissions().contains(permission.code());
    }

    private static boolean isSpaceScoped(Subject subject) {
        return "SPACE".equals(subject.scopeLevel()) && subject.spaceId() != null;
    }

    // POST /api/v1/orgs/signup: the bootstrap remains authenticated as before this
    // story, but its authorization is now explicit. Every other action is denied
    // instead of falling through to POL_DEFAULT_ALLOW.
    private static Optional<PolicyDecision> organizationSignupPolicy(String path, Action action) {
        if (!"/api/v1/orgs/signup".equals(path)) {
            return Optional.empty();
        }
        if (action != Action.CREATE) {
            return deny("POL_ORG_SIGNUP_ACTION_NOT_SUPPORTED",
                    "Only CREATE is governed on the organization signup route");
        }
        return permit("POL_ORG_SIGNUP_AUTHENTICATED_CREATE",
                "An authenticated caller may bootstrap a new organization");
    }

    // /api/v1/me/spaces (IAM 32) : surface personnelle, sans rôle, mais strictement
    // réservée à un humain authentifié au niveau ORGANIZATION avec org_id/account_id.
    private static Optional<PolicyDecision> currentUserSpacesPolicy(Subject subject, String path, Action action) {
        if (!"/api/v1/me/spaces".equals(path)) {
            return Optional.empty();
        }
        if (action != Action.READ
                || !Subject.TYPE_HUMAN.equals(subject.subjectType())
                || !"ORGANIZATION".equals(subject.scopeLevel())
                || subject.orgId() == null
                || subject.orgId().isBlank()
                || subject.accountId() == null
                || subject.accountId().isBlank()
                || subject.spaceId() != null) {
            return deny("POL_MY_SPACES_ORG_HUMAN_REQUIRED",
                    "Organization-scoped human account token required to list accessible spaces");
        }
        return Optional.of(PolicyDecision.builder()
                .effect(Effect.PERMIT)
                .policyId("POL_MY_SPACES_ORG_HUMAN_REQUIRED")
                .reason("Organization-scoped human account token may list its own accessible spaces")
                .build());
    }

    // /api/v1/orgs/{UUID}/dashboard/** (récit Dashboard 01) : surface FAIL-CLOSED,
    // à l'image de la surface OAuth2. Plan ORGANIZATION : sujet HUMAIN de portée
    // ORGANIZATION dont l'org == org du chemin, et permission P_ORG_READ. Un membre, un
    // token SPACE, un token PLATFORM ou d'une autre org : refusés. Seul /dashboard/summary en READ est gouverné : toute autre sous-route
    // (POL_ORG_DASHBOARD_ROUTE_NOT_GOVERNED) ou action (POL_ORG_DASHBOARD_ACTION_NOT_SUPPORTED)
    // est refusée — rien de cette surface ne retombe jamais sur POL_DEFAULT_ALLOW.
    private static Optional<PolicyDecision> denyOrgDashboardSurface(Subject subject, String path,
                                                                    Action action) {
        OrgDashboardRoute route = OrgDashboardRoute.parse(path);
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

    // /api/v1/orgs/{orgCode}/spaces/{spaceCode}/{users|roles|groups|permissions}[...]
    // (routes lisibles, plan SPACE). Chaque paire route/action exige sa permission P_SPACE_*
    // et un token situé dans un Space : la correspondance du Space désigné par les codes reste
    // vérifiée par SpaceBoundaryGuard, qui seul résout ces codes. Paire non gouvernée, y compris
    // une méthode sans endpoint : 403 (POL_SPACE_ROUTE_NOT_GOVERNED), jamais 405, comme sur les
    // surfaces UUID — la réponse ne révèle pas quelles méthodes existent.
    private static Optional<PolicyDecision> readableSpaceSurface(Subject subject, String path, Action action) {
        ReadableSpaceRoute route = ReadableSpaceRoute.parse(path);
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
    record ReadableSpaceRoute(String[] rest) {

        private static final java.util.Set<String> LIFECYCLE =
                java.util.Set.of("suspend", "activate", "lock", "deactivate");

        static ReadableSpaceRoute parse(String path) {
            // ["", "api", "v1", "orgs", {orgCode}, "spaces", {spaceCode}, {surface}, ...]
            String[] seg = path.split("/");
            boolean onSurface = seg.length >= 8
                    && seg[0].isEmpty()
                    && "api".equals(seg[1]) && "v1".equals(seg[2])
                    && "orgs".equals(seg[3]) && "spaces".equals(seg[5])
                    && !isUuid(seg[4])
                    && java.util.Set.of("users", "roles", "groups", "permissions").contains(seg[7]);
            return onSurface ? new ReadableSpaceRoute(java.util.Arrays.copyOfRange(seg, 7, seg.length)) : null;
        }

        String policyId() {
            if (!"users".equals(rest[0])) {
                return "POL_RBAC_READ_ADMIN_REQUIRED";
            }
            return rest.length >= 3 && ("roles".equals(rest[2]) || "groups".equals(rest[2]))
                    ? "POL_USER_RBAC_ADMIN_REQUIRED"
                    : "POL_USER_ADMIN_REQUIRED";
        }

        TechnicalPermission requiredPermission(Action action) {
            if (!"users".equals(rest[0])) {
                // Catalogue RBAC du Space : lecture seule.
                return rest.length <= 2 && action == Action.READ ? TechnicalPermission.SPACE_RBAC_READ : null;
            }
            return switch (rest.length) {
                case 1 -> action == Action.READ ? TechnicalPermission.SPACE_USERS_READ
                        : action == Action.CREATE ? TechnicalPermission.SPACE_USERS_MANAGE : null;
                case 2 -> action == Action.READ ? TechnicalPermission.SPACE_USERS_READ
                        : action == Action.UPDATE ? TechnicalPermission.SPACE_USERS_MANAGE : null;
                case 3 -> {
                    if (LIFECYCLE.contains(rest[2])) {
                        yield action == Action.CREATE ? TechnicalPermission.SPACE_USERS_LIFECYCLE : null;
                    }
                    if ("roles".equals(rest[2]) || "groups".equals(rest[2])) {
                        yield action == Action.READ ? TechnicalPermission.SPACE_RBAC_READ
                                : action == Action.CREATE ? TechnicalPermission.SPACE_RBAC_ASSIGN : null;
                    }
                    yield null;
                }
                case 4 -> ("roles".equals(rest[2]) || "groups".equals(rest[2])) && action == Action.DELETE
                        ? TechnicalPermission.SPACE_RBAC_ASSIGN : null;
                default -> null;
            };
        }
    }

    // /api/v1/orgs/{UUID}/spaces/{UUID}/clients[...] (management des clients OAuth2)
    // Règle POL_OAUTH_CLIENT_ADMIN_REQUIRED : créer un client machine ou faire tourner
    // son secret est l'acte le plus sensible du tenant (le secret sort en clair).
    // Exigences cumulatives : sujet HUMAIN, token de portée SPACE (org_id ET space_id
    // présents), org et space du token == org et space du chemin, et la permission
    // P_SPACE_CLIENTS_MANAGE (création) ou P_SPACE_CLIENTS_ROTATE_SECRET (rotation).
    // Un token ORGANIZATION (sans space_id) est REFUSÉ : la surface reste volontairement
    // inutilisable pour lui jusqu'à l'échange ORG->SPACE (IAM 34) — fail-closed assumé.
    // Cette règle gouverne TOUTE la surface clients : rien n'y retombe jamais sur
    // POL_DEFAULT_ALLOW.
    private static Optional<PolicyDecision> denyOAuthClientSurface(Subject subject, String path, Action action) {
        OAuthClientRoute route = OAuthClientRoute.parse(path);
        if (route == null) {
            return Optional.empty();
        }

        if (!subject.isHuman()) {
            return deny("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                    "Only a HUMAN subject may manage OAuth clients");
        }
        if (subject.orgId() == null || !subject.orgId().equalsIgnoreCase(route.orgId())) {
            return deny(POL_ORG_MISMATCH,
                    "Token organization does not match the target organization");
        }
        if (!"SPACE".equals(subject.scopeLevel())) {
            return deny("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                    "A SPACE-scoped token is required");
        }
        if (subject.spaceId() == null || !subject.spaceId().equalsIgnoreCase(route.spaceId())) {
            return deny("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                    "A SPACE-scoped token designating the target space is required to manage OAuth clients");
        }
        if (route.kind() == OAuthClientRouteKind.UNKNOWN) {
            return deny("POL_OAUTH_CLIENT_ROUTE_NOT_GOVERNED",
                    "No policy governs this OAuth client route yet");
        }
        if (action != Action.CREATE) {
            return deny("POL_OAUTH_CLIENT_ACTION_NOT_SUPPORTED",
                    "Only CREATE is governed on the OAuth clients surface");
        }
        TechnicalPermission required = route.kind() == OAuthClientRouteKind.ROTATE_SECRET
                ? TechnicalPermission.SPACE_CLIENTS_ROTATE_SECRET
                : TechnicalPermission.SPACE_CLIENTS_MANAGE;
        if (!hasPermission(subject, required)) {
            return deny("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                    required.code() + " required to manage OAuth clients");
        }
        return permit("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                required.code() + " holder bound to the target space may manage OAuth clients");
    }

    /**
     * Route de la surface clients OAuth2 : /api/v1/orgs/{UUID}/spaces/{UUID}/clients
     * et toutes ses sous-routes ({id}/rotate-secret, ...). Le discriminant UUID suit
     * la même doctrine que {@link TmsSpaceRoute} : les routes en codes lisibles ne
     * matchent jamais.
     */
    enum OAuthClientRouteKind {
        COLLECTION_CREATE,
        ROTATE_SECRET,
        UNKNOWN
    }

    record OAuthClientRoute(String orgId, String spaceId, OAuthClientRouteKind kind) {

        static OAuthClientRoute parse(String path) {
            // ["", "api", "v1", "orgs", {orgId}, "spaces", {spaceId}, "clients", ...]
            String[] seg = path.split("/");
            boolean onSurface = seg.length >= 8
                    && seg[0].isEmpty()
                    && "api".equals(seg[1]) && "v1".equals(seg[2])
                    && "orgs".equals(seg[3]) && "spaces".equals(seg[5])
                    && "clients".equals(seg[7])
                    && isUuid(seg[4]) && isUuid(seg[6]);
            if (!onSurface) {
                return null;
            }
            if (seg.length == 8) {
                return new OAuthClientRoute(seg[4], seg[6], OAuthClientRouteKind.COLLECTION_CREATE);
            }
            if (seg.length == 10 && isUuid(seg[8]) && "rotate-secret".equals(seg[9])) {
                return new OAuthClientRoute(seg[4], seg[6], OAuthClientRouteKind.ROTATE_SECRET);
            }
            return new OAuthClientRoute(seg[4], seg[6], OAuthClientRouteKind.UNKNOWN);
        }
    }

    // /api/v1/orgs/{orgId}/spaces/** (surface TMS, plan de management)
    // Doctrine d'identification : TMS parle en UUID, TIS-CORE en codes lisibles.
    // Le discriminant UUID laisse donc les routes lisibles (codes) à leurs règles dédiées.
    // La surface est FAIL-CLOSED : seules les paires route/action explicitement
    // gouvernées peuvent être permises — collection {READ, CREATE}, détail {READ}.
    // Toute autre action est refusée (POL_SPACE_ACTION_NOT_SUPPORTED), toute
    // sous-route pas encore gouvernée aussi (POL_SPACE_ROUTE_NOT_GOVERNED) :
    // rien de cette surface ne retombe jamais sur POL_DEFAULT_ALLOW.
    private static Optional<PolicyDecision> denyTmsSpaceSurface(Subject subject, String path, Action action) {
        // La surface clients OAuth2 (.../spaces/{uuid}/clients[...]) est gouvernée par
        // sa règle dédiée (denyOAuthClientSurface, évaluée avant) : elle est retirée de
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

    /**
     * Terminal lock for the TMS surface. After the explicit signup, dashboard,
     * spaces and clients policies, any other route using TMS UUID identification is
     * denied. TIS-CORE routes using readable organization codes remain out of scope.
     */
    private static Optional<PolicyDecision> denyUngovernedTmsSurface(String path) {
        String[] segments = path.split("/");
        boolean tmsOrganizationRoute = segments.length >= 5
                && segments[0].isEmpty()
                && "api".equals(segments[1])
                && "v1".equals(segments[2])
                && "orgs".equals(segments[3])
                && isUuid(segments[4]);

        if (!tmsOrganizationRoute) {
            return Optional.empty();
        }
        return deny("POL_TMS_ROUTE_NOT_GOVERNED",
                "No explicit policy governs this TMS route - denied by default");
    }

    private static boolean isUuid(String value) {
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean sameUuid(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        try {
            return java.util.UUID.fromString(expected).equals(java.util.UUID.fromString(actual));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Optional<PolicyDecision> deny(String policyId, String reason) {
        return Optional.of(PolicyDecision.builder()
                .effect(Effect.DENY)
                .policyId(policyId)
                .reason(reason)
                .build());
    }

    private static Optional<PolicyDecision> permit(String policyId, String reason) {
        return Optional.of(PolicyDecision.builder()
                .effect(Effect.PERMIT)
                .policyId(policyId)
                .reason(reason)
                .build());
    }
}
