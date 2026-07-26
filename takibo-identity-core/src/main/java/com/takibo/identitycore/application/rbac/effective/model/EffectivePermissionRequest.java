package com.takibo.identitycore.application.rbac.effective.model;

import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Complete situated input for effective-permission resolution.
 *
 * <p>The role and group sets contain real assignments only. The resolver never
 * manufactures a role to represent a projected permission.</p>
 */
public record EffectivePermissionRequest(
        Set<SituatedTechnicalRole> roles,
        Set<SituatedTechnicalGroup> groups,
        AuthorityPlan authorityPlan,
        UUID orgId,
        UUID spaceId,
        RbacSubjectNature subjectNature,
        RbacActorSource actorSource
) {
    public EffectivePermissionRequest {
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        groups = Set.copyOf(Objects.requireNonNull(groups, "groups"));
        Objects.requireNonNull(authorityPlan, "authorityPlan");
        Objects.requireNonNull(subjectNature, "subjectNature");
        Objects.requireNonNull(actorSource, "actorSource");
    }
}
