package com.takibo.identitycore.application.rbac.effective.model;

import com.takibo.identitycore.domain.catalogrbac.TechnicalGroup;

import java.util.Objects;
import java.util.UUID;

/**
 * A real technical-group membership together with its persisted authority boundary.
 */
public record SituatedTechnicalGroup(
        TechnicalGroup group,
        UUID orgId,
        UUID spaceId
) {
    public SituatedTechnicalGroup {
        Objects.requireNonNull(group, "group");
    }
}
