package com.takibo.securitymanagement.domain.service;

import com.takibo.securitymanagement.domain.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    // ─────────────────────────────────────────────────────────────
    private PolicyDecision evaluateResourcePolicies(Subject subject,
                                                   Resource resource,
                                                   Action action) {

        String path = resource.path();

        // Helper rôle
        boolean isPlatformAdmin = subject.roles().contains("PLATFORM_ADMIN");
        boolean isOrgAdmin      = subject.roles().contains("ORG_ADMIN");
        boolean isSpaceAdmin    = subject.roles().contains("SPACE_ADMIN");

        // PLATFORM_ADMIN = super admin tenant
        boolean isTenantAdmin = isPlatformAdmin || isOrgAdmin || isSpaceAdmin;

        // ───────────────────────────────
        // /api/spaces/signup (public)
        // => déjà permis dans SecurityConfig (permitAll)
        // ───────────────────────────────

        // ───────────────────────────────
        // /api/spaces/{spaceId}/users (création d'utilisateur)
        // ───────────────────────────────
        if (path.startsWith("/api/spaces/") && path.contains("/users")) {

            if (action == Action.CREATE) {
                if (!isTenantAdmin) {
                    return PolicyDecision.builder()
                            .effect(Effect.DENY)
                            .policyId("POL_USER_CREATE_ADMIN_REQUIRED")
                            .reason("ORG_ADMIN or SPACE_ADMIN or PLATFORM_ADMIN required to create user")
                            .build();
                }
            }
        }

        // ───────────────────────────────
        // /api/spaces/{spaceId}/clients (création de clients OAuth2)
        // ───────────────────────────────
        if (path.startsWith("/api/spaces/") && path.contains("/clients")) {

            if (action == Action.CREATE) {
                if (!isTenantAdmin) {
                    return PolicyDecision.builder()
                            .effect(Effect.DENY)
                            .policyId("POL_CLIENT_CREATE_ADMIN_REQUIRED")
                            .reason("ORG_ADMIN or SPACE_ADMIN or PLATFORM_ADMIN required to create client")
                            .build();
                }
            }
        }

        // ───────────────────────────────
        // /api/users/** – générique (si tu en as)
        // ───────────────────────────────
        if (path.startsWith("/api/users")) {

            // DELETE nécessite permission USER_DELETE
            if (action == Action.DELETE) {
                if (!subject.permissions().contains("USER_DELETE")) {
                    return PolicyDecision.builder()
                            .effect(Effect.DENY)
                            .policyId("POL_USER_DELETE_PERMISSION")
                            .reason("USER_DELETE permission required")
                            .build();
                }
            }

            // CREATE nécessite un admin tenant
            if (action == Action.CREATE && !isTenantAdmin) {
                return PolicyDecision.builder()
                        .effect(Effect.DENY)
                        .policyId("POL_USER_CREATE_ADMIN_REQUIRED")
                        .reason("ORG_ADMIN or SPACE_ADMIN or PLATFORM_ADMIN required")
                        .build();
            }
        }

        // Aucune règle n'a bloqué → on autorise
        return PolicyDecision.builder()
                .effect(Effect.PERMIT)
                .policyId("POL_RESOURCE_ALLOW")
                .reason("No specific resource policy denied access")
                .build();
    }
}
