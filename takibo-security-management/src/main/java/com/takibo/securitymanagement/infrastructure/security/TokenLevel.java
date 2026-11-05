package com.takibo.securitymanagement.infrastructure.security;

import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Niveau d'un token, établi à partir des seuls claims signés et concordants
 * ({@code takibo_scope_level}, {@code org_id}, {@code space_id}).
 */
enum TokenLevel {

    SPACE(EnumSet.of(AuthorityPlan.ORGANIZATION, AuthorityPlan.SPACE)),
    ORGANIZATION(EnumSet.of(AuthorityPlan.ORGANIZATION)),
    PLATFORM(EnumSet.of(AuthorityPlan.PLATFORM)),
    /** Niveau absent ou incohérent avec les identifiants du token : ni rôle ni permission admis. */
    NONE(EnumSet.noneOf(AuthorityPlan.class));

    private final Set<AuthorityPlan> admittedPlans;

    TokenLevel(Set<AuthorityPlan> admittedPlans) {
        this.admittedPlans = admittedPlans;
    }

    /** Plans des rôles du catalogue admis à ce niveau (ORGANIZATION reste valable en SPACE). */
    boolean admits(AuthorityPlan plan) {
        return admittedPlans.contains(plan);
    }

    static TokenLevel of(Map<String, Object> claims) {
        Object scopeLevel = claims.get("scopeLevel");
        boolean org = isPresent(claims.get("orgId"));
        boolean space = isPresent(claims.get("spaceId"));
        if ("SPACE".equals(scopeLevel) && org && space) {
            return SPACE;
        }
        if ("ORGANIZATION".equals(scopeLevel) && org && !space) {
            return ORGANIZATION;
        }
        if ("PLATFORM".equals(scopeLevel) && !org && !space) {
            return PLATFORM;
        }
        return NONE;
    }

    private static boolean isPresent(Object value) {
        return value != null && !value.toString().isBlank();
    }

    /**
     * Plan des permissions qu'un token de ce niveau peut porter : exactement le sien. La
     * projection ORGANIZATION vers SPACE a lieu à l'émission (EffectivePermissionResolver) et
     * produit des permissions SPACE ; un token SPACE ne porte donc jamais de permission ORG.
     */
    Optional<AuthorityPlan> permissionPlan() {
        return switch (this) {
            case SPACE -> Optional.of(AuthorityPlan.SPACE);
            case ORGANIZATION -> Optional.of(AuthorityPlan.ORGANIZATION);
            case PLATFORM -> Optional.of(AuthorityPlan.PLATFORM);
            case NONE -> Optional.empty();
        };
    }
}
