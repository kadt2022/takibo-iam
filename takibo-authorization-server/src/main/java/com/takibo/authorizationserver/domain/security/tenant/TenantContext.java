package com.takibo.authorizationserver.domain.security.tenant;

import java.util.UUID;

/**
 * Tenant context (orgId + spaceId) for current request.
 * Immutable and thread-safe.
 * 
 * @param orgId Organization ID
 * @param spaceId Space ID
 */
public record TenantContext(UUID orgId, UUID spaceId) {
    
    public TenantContext {
        if (orgId == null) {
            throw new IllegalArgumentException("orgId cannot be null");
        }
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId cannot be null");
        }
    }
}
