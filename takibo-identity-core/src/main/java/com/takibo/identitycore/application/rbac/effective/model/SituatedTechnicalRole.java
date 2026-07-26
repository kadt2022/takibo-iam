package com.takibo.identitycore.application.rbac.effective.model;

import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;

import java.util.Objects;
import java.util.UUID;

/**
 * A real technical-role assignment together with its persisted authority boundary.
 */
public record SituatedTechnicalRole(
        TechnicalRole role,
        UUID orgId,
        UUID spaceId
) {
    public SituatedTechnicalRole {
        Objects.requireNonNull(role, "role");
    }
}
