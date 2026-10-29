package com.takibo.iamboot.support;

import com.takibo.identitycore.application.rbac.effective.model.RbacActorSource;
import com.takibo.identitycore.application.rbac.effective.model.RbacSubjectNature;
import com.takibo.identitycore.application.rbac.effective.model.EffectivePermissionRequest;
import com.takibo.identitycore.application.rbac.effective.model.SituatedTechnicalRole;
import com.takibo.identitycore.application.rbac.effective.service.EffectivePermissionResolver;
import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;
import com.takibo.identitycore.domain.catalogrbac.RolePermissionCatalog;
import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.integration.space.port.SpaceManagementCase;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Permissions situées qu'un token humain porte réellement pour des rôles donnés, calculées
 * par le vrai résolveur et le vrai catalogue (projection ORGANIZATION -> SPACE comprise),
 * comme à l'émission. Les tests de politique restent ainsi fidèles aux tokens de production.
 */
public final class TokenPermissions {

    private TokenPermissions() {
    }

    public static List<String> of(Collection<String> roles, String orgId, String spaceId, String scopeLevel) {
        AuthorityPlan plan = plan(scopeLevel);
        if (plan == null) {
            return List.of();
        }
        UUID org = uuid(orgId);
        UUID space = plan == AuthorityPlan.SPACE ? uuid(spaceId) : null;
        Set<SituatedTechnicalRole> situated = roles.stream()
                .map(TechnicalRole::fromCode)
                .flatMap(Optional::stream)
                .map(role -> new SituatedTechnicalRole(role,
                        role.plan() == AuthorityPlan.PLATFORM ? null : org,
                        role.plan() == AuthorityPlan.SPACE ? space : null))
                .collect(Collectors.toUnmodifiableSet());

        SpaceManagementCase spaces = mock(SpaceManagementCase.class);
        when(spaces.findOrgIdBySpaceId(any())).thenReturn(Optional.ofNullable(org));
        try {
            return new EffectivePermissionResolver(new RolePermissionCatalog(), spaces)
                    .resolve(new EffectivePermissionRequest(situated, Set.of(), plan,
                            plan == AuthorityPlan.PLATFORM ? null : org, space,
                            RbacSubjectNature.HUMAN, RbacActorSource.HUMAN))
                    .stream().map(code -> code.code()).sorted().toList();
        } catch (RuntimeException refused) {
            // Le résolveur refuse une combinaison qu'aucun token ne pourrait porter.
            return List.of();
        }
    }

    private static AuthorityPlan plan(String scopeLevel) {
        if (scopeLevel == null) {
            return null;
        }
        return switch (scopeLevel) {
            case "SPACE" -> AuthorityPlan.SPACE;
            case "ORGANIZATION" -> AuthorityPlan.ORGANIZATION;
            case "PLATFORM" -> AuthorityPlan.PLATFORM;
            default -> null;
        };
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
