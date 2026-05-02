package com.takibo.authorizationserver.domain.security.tenant;

import com.takibo.authorizationserver.domain.exception.TakiboServerErrorException;
import com.takibo.authorizationserver.domain.exception.TenantNotFoundException;

/**
 * Resolves tenant context (orgId + spaceId) from client_id.
 * 
 * Implementations typically call TMS (Takibo Management Service) to lookup
 * client registration and extract tenant information.
 */
public interface TenantResolver {
    
    /**
     * Resolve tenant context from OAuth2 client ID.
     * 
     * @param clientId OAuth2 client ID
     * @return Tenant context (never null)
     * @throws TenantNotFoundException if client not found
     * @throws TakiboServerErrorException.TenantResolutionException if resolution fails
     */
    TenantContext resolve(String clientId);
}
