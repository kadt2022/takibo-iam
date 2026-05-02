package com.takibo.securitycontext.model;

import java.util.UUID;

import static com.takibo.securitycontext.validation.TakiboAsserts.isTrue;
import static com.takibo.securitycontext.validation.TakiboSecurityContextValidators.normalizeToNull;
import static com.takibo.securitycontext.validation.TakiboSecurityContextValidators.validateUuid;

public record TenantScope(
        String organizationId,
        String spaceId
) {

    public TenantScope {
        organizationId = normalizeToNull(organizationId);
        spaceId = normalizeToNull(spaceId);

        if (organizationId != null) validateUuid(organizationId, "organizationId");
        if (spaceId != null) validateUuid(spaceId, "spaceId");

        isTrue(organizationId != null || spaceId == null, "spaceId requires organizationId");
    }

    public boolean isOrgScoped() {
        return organizationId != null;
    }

    public boolean isSpaceScoped() {
        return organizationId != null && spaceId != null;
    }
}
