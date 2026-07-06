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
        if (resourcePolicy.isDeny()) {
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

        return denyLegacyUserCreation(path, action, tenantAdmin)
                .or(() -> denyReadableUsersSurface(path, tenantAdmin))
                .or(() -> denyReadableRbacCatalogSurface(path, tenantAdmin))
                .or(() -> denyLegacyClientCreation(path, action, tenantAdmin))
                .or(() -> denyGenericUsersSurface(subject, path, action, tenantAdmin))
                .orElseGet(() -> PolicyDecision.builder()
                        .effect(Effect.PERMIT)
                        .policyId("POL_RESOURCE_ALLOW")
                        .reason("No specific resource policy denied access")
                        .build());
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

    // /api/spaces/{spaceId}/users (création d'utilisateur, route UUID historique)
    private static Optional<PolicyDecision> denyLegacyUserCreation(String path, Action action, boolean tenantAdmin) {
        if (path.startsWith("/api/spaces/") && path.contains("/users")
                && action == Action.CREATE && !tenantAdmin) {
            return deny("POL_USER_CREATE_ADMIN_REQUIRED",
                    "ORG_ADMIN or SPACE_ADMIN or PLATFORM_ADMIN required to create user");
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
}
