package com.takibo.securitymanagement.domain.service;

import com.takibo.securitymanagement.domain.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class PolicyEvaluator {

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
    // ─────────────────────────────────────────────────────────────
    private PolicyDecision evaluateTenantIsolation(Subject subject, Resource resource) {

        // Isolation par organisation (si on a l'info)
        if (resource.orgId() != null && subject.orgId() != null &&
                !resource.orgId().equals(subject.orgId())) {

            log.warn("Tenant isolation violation: subject.orgId={}, resource.orgId={}",
                    subject.orgId(), resource.orgId());

            return PolicyDecision.builder()
                    .effect(Effect.DENY)
                    .policyId("POL_ORG_MISMATCH")
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
    // NB : /api/spaces/signup est public (permitAll dans SecurityConfig).
    // ─────────────────────────────────────────────────────────────
    private PolicyDecision evaluateResourcePolicies(Subject subject,
                                                   Resource resource,
                                                   Action action) {

        String path = resource.path();
        boolean tenantAdmin = isTenantAdmin(subject);

        // ORDONNANCEMENT : la règle clients OAuth2 DOIT précéder denyTmsSpaceSurface —
        // TmsSpaceRoute.parse classe .../spaces/{uuid}/clients comme sous-route et la
        // refuserait pour tous (POL_SPACE_ROUTE_NOT_GOVERNED), admins légitimes compris.
        return denyLegacyUserCreation(path, action, tenantAdmin)
                .or(() -> currentUserSpacesPolicy(subject, path, action))
                .or(() -> denyUserRbacGovernanceSurface(path, tenantAdmin))
                .or(() -> denyReadableUsersSurface(path, tenantAdmin))
                .or(() -> denyReadableRbacCatalogSurface(path, tenantAdmin))
                .or(() -> denyOAuthClientSurface(subject, path, action))
                .or(() -> denyTmsSpaceSurface(subject, path, action))
                .or(() -> denyLegacyClientCreation(path, action, tenantAdmin))
                .or(() -> denyGenericUsersSurface(subject, path, action, tenantAdmin))
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
     * Admin tenant = pouvoir d'agir dans la frontière portée par le token.
     * Ce statut n'élargit JAMAIS la frontière elle-même (token.space_id reste strict).
     * Les codes réels du catalogue technique (R_*) sont la référence ; les anciens
     * alias sans préfixe restent acceptés pour compatibilité.
     */
    private static boolean isTenantAdmin(Subject subject) {
        return hasAnyRole(subject, "R_PLATFORM_ADMIN", "PLATFORM_ADMIN")
                || hasAnyRole(subject, "R_ORG_OWNER", "ORG_OWNER")
                || hasAnyRole(subject, "R_ORG_ADMIN", "ORG_ADMIN")
                || hasAnyRole(subject, "R_SPACE_ADMIN", "SPACE_ADMIN");
    }

    private static boolean hasAnyRole(Subject subject, String... codes) {
        for (String code : codes) {
            if (subject.roles().contains(code)) {
                return true;
            }
        }
        return false;
    }

    // /api/v1/me/spaces (IAM 32) : surface personnelle, sans rôle, mais strictement
    // réservée à un humain authentifié au niveau ORGANIZATION avec org_id/account_id.
    private static Optional<PolicyDecision> currentUserSpacesPolicy(Subject subject, String path, Action action) {
        if (!"/api/v1/me/spaces".equals(path)) {
            return Optional.empty();
        }
        if (action != Action.READ
                || !"HUMAN".equals(subject.subjectType())
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

    // /api/spaces/{spaceId}/users (création d'utilisateur, route UUID historique)
    private static Optional<PolicyDecision> denyLegacyUserCreation(String path, Action action, boolean tenantAdmin) {
        if (path.startsWith("/api/spaces/") && path.contains("/users")
                && action == Action.CREATE && !tenantAdmin) {
            return deny("POL_USER_CREATE_ADMIN_REQUIRED",
                    "ORG_ADMIN or SPACE_ADMIN or PLATFORM_ADMIN required to create user");
        }
        return Optional.empty();
    }

    // /api/v1/orgs/{orgCode}/spaces/{spaceCode}/users/{userId}/{roles|groups}[...] (route lisible)
    // Déléguer/retirer le pouvoir d'un user est l'acte d'admin le plus sensible de la
    // surface : règle dédiée, évaluée AVANT la règle générale users.
    private static Optional<PolicyDecision> denyUserRbacGovernanceSurface(String path, boolean tenantAdmin) {
        if (isUserRbacGovernanceRoute(path) && !tenantAdmin) {
            return deny("POL_USER_RBAC_ADMIN_REQUIRED",
                    "R_ORG_OWNER, R_ORG_ADMIN or R_SPACE_ADMIN required to govern user roles and groups");
        }
        return Optional.empty();
    }

    // /api/v1/orgs/{orgCode}/spaces/{spaceCode}/users[...] (route lisible)
    // Toute la surface user (list/get/create/patch/lifecycle) est un acte d'admin :
    // lecture comprise — un membre sans rôle admin ne voit pas l'annuaire du space.
    // Le rôle autorise l'action ; la frontière reste token.org_id/space_id.
    private static Optional<PolicyDecision> denyReadableUsersSurface(String path, boolean tenantAdmin) {
        if (isReadableUsersRoute(path) && !tenantAdmin) {
            return deny("POL_USER_ADMIN_REQUIRED",
                    "R_ORG_OWNER, R_ORG_ADMIN or R_SPACE_ADMIN required to administer users");
        }
        return Optional.empty();
    }

    // /api/v1/orgs/{orgCode}/spaces/{spaceCode}/{roles|groups|permissions}[...] (route lisible)
    // Le catalogue RBAC décrit la structure du pouvoir : sa lecture est un acte
    // d'admin tenant, pas une liste publique pour tout membre du space.
    private static Optional<PolicyDecision> denyReadableRbacCatalogSurface(String path, boolean tenantAdmin) {
        if (isReadableRbacCatalogRoute(path) && !tenantAdmin) {
            return deny("POL_RBAC_READ_ADMIN_REQUIRED",
                    "R_ORG_OWNER, R_ORG_ADMIN or R_SPACE_ADMIN required to read the RBAC catalog");
        }
        return Optional.empty();
    }

    // /api/v1/orgs/{UUID}/spaces/{UUID}/clients[...] (management des clients OAuth2)
    // Règle POL_OAUTH_CLIENT_ADMIN_REQUIRED : créer un client machine ou faire tourner
    // son secret est l'acte le plus sensible du tenant (le secret sort en clair).
    // Exigences cumulatives : sujet HUMAIN, token de portée SPACE (org_id ET space_id
    // présents), org et space du token == org et space du chemin, et un rôle d'admin
    // tenant (R_ORG_OWNER / R_ORG_ADMIN / R_SPACE_ADMIN, alias legacy acceptés).
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
            return deny("POL_ORG_MISMATCH",
                    "Token organization does not match the target organization");
        }
        if (subject.spaceId() == null || !subject.spaceId().equalsIgnoreCase(route.spaceId())) {
            return deny("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                    "A SPACE-scoped token designating the target space is required to manage OAuth clients");
        }
        if (action != Action.CREATE) {
            return deny("POL_OAUTH_CLIENT_ACTION_NOT_SUPPORTED",
                    "Only CREATE is governed on the OAuth clients surface");
        }
        if (!hasAnyRole(subject, "R_ORG_OWNER", "ORG_OWNER", "R_ORG_ADMIN", "ORG_ADMIN",
                "R_SPACE_ADMIN", "SPACE_ADMIN")) {
            return deny("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                    "R_ORG_OWNER, R_ORG_ADMIN or R_SPACE_ADMIN required to manage OAuth clients");
        }
        return Optional.empty();
    }

    /**
     * Route de la surface clients OAuth2 : /api/v1/orgs/{UUID}/spaces/{UUID}/clients
     * et toutes ses sous-routes ({id}/rotate-secret, ...). Le discriminant UUID suit
     * la même doctrine que {@link TmsSpaceRoute} : les routes en codes lisibles ne
     * matchent jamais.
     */
    record OAuthClientRoute(String orgId, String spaceId) {

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
            return new OAuthClientRoute(seg[4], seg[6]);
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
            return deny("POL_ORG_MISMATCH",
                    "Token organization does not match the target organization");
        }

        if (route.isSubRoute()) {
            return deny("POL_SPACE_ROUTE_NOT_GOVERNED",
                    "No policy governs this space route yet — denied by default");
        }

        boolean orgAuthority = hasAnyRole(subject, "R_ORG_OWNER", "ORG_OWNER", "R_ORG_ADMIN", "ORG_ADMIN");

        if (route.isCollectionRoute()) {
            if (action != Action.READ && action != Action.CREATE) {
                return deny("POL_SPACE_ACTION_NOT_SUPPORTED",
                        "Only READ and CREATE are governed on the spaces collection");
            }
            if (orgAuthority) {
                return Optional.empty();
            }
            return action == Action.READ
                    ? deny("POL_SPACE_LIST_ORG_AUTHORITY_REQUIRED",
                            "R_ORG_OWNER or R_ORG_ADMIN required to list the spaces of an organization")
                    : deny("POL_SPACE_CREATE_ORG_AUTHORITY_REQUIRED",
                            "R_ORG_OWNER or R_ORG_ADMIN required to create a space in an organization");
        }

        // Détail : seule la lecture est gouvernée — UPDATE/DELETE/lifecycle restent
        // fermés même pour une autorité ORG, jusqu'au récit qui les gouvernera.
        if (action != Action.READ) {
            return deny("POL_SPACE_ACTION_NOT_SUPPORTED",
                    "Only READ is governed on a space detail route");
        }
        if (orgAuthority) {
            return Optional.empty();
        }
        // Exception locale, strictement READ : un R_SPACE_ADMIN lit le space que
        // son token désigne déjà (org ET space du chemin == org ET space du token).
        boolean localSpaceAdmin = hasAnyRole(subject, "R_SPACE_ADMIN", "SPACE_ADMIN")
                && route.spaceId().equalsIgnoreCase(subject.spaceId());
        if (localSpaceAdmin) {
            return Optional.empty();
        }
        return deny("POL_SPACE_READ_ORG_OR_LOCAL_ADMIN_REQUIRED",
                "R_ORG_OWNER/R_ORG_ADMIN required, or R_SPACE_ADMIN of this space for read");
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

    private static boolean isUuid(String value) {
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // /api/spaces/{spaceId}/clients (création de clients OAuth2, route UUID historique)
    private static Optional<PolicyDecision> denyLegacyClientCreation(String path, Action action, boolean tenantAdmin) {
        if (path.startsWith("/api/spaces/") && path.contains("/clients")
                && action == Action.CREATE && !tenantAdmin) {
            return deny("POL_CLIENT_CREATE_ADMIN_REQUIRED",
                    "ORG_ADMIN or SPACE_ADMIN or PLATFORM_ADMIN required to create client");
        }
        return Optional.empty();
    }

    // /api/users/** – générique
    private static Optional<PolicyDecision> denyGenericUsersSurface(Subject subject, String path,
                                                                    Action action, boolean tenantAdmin) {
        if (!path.startsWith("/api/users")) {
            return Optional.empty();
        }
        if (action == Action.DELETE && !subject.permissions().contains("USER_DELETE")) {
            return deny("POL_USER_DELETE_PERMISSION", "USER_DELETE permission required");
        }
        if (action == Action.CREATE && !tenantAdmin) {
            return deny("POL_USER_CREATE_ADMIN_REQUIRED",
                    "ORG_ADMIN or SPACE_ADMIN or PLATFORM_ADMIN required");
        }
        return Optional.empty();
    }

    private static Optional<PolicyDecision> deny(String policyId, String reason) {
        return Optional.of(PolicyDecision.builder()
                .effect(Effect.DENY)
                .policyId(policyId)
                .reason(reason)
                .build());
    }

    private static boolean isReadableUsersRoute(String path) {
        return path.startsWith("/api/v1/orgs/")
                && path.contains("/spaces/")
                && path.contains("/users");
    }

    private static boolean isReadableRbacCatalogRoute(String path) {
        return path.startsWith("/api/v1/orgs/")
                && path.contains("/spaces/")
                && (path.contains("/roles") || path.contains("/groups") || path.contains("/permissions"));
    }

    private static boolean isUserRbacGovernanceRoute(String path) {
        return isReadableUsersRoute(path)
                && (path.contains("/roles") || path.contains("/groups"));
    }
}
