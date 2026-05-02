package com.takibo.authorizationserver.domain.security.tenant;

import com.takibo.authorizationserver.domain.exception.TenantNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Stub tenant resolver for Sprint 1 development.
 * Always resolves to default org/space.
 * 
 * Replace with TmsTenantResolver when TMS adapter is ready.
 */
@Slf4j
@Service
public class StubTenantResolver implements TenantResolver {
    
    // Default tenant for Sprint 1
    private static final UUID DEFAULT_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEFAULT_SPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    
    @Override
    public TenantContext resolve(String clientId) {
        log.debug("Resolving tenant (stub: always returns default tenant)");

        // Validation removed - filter handles blank clientId validation
        // Filter will throw TakiboInvalidRequestException for blank/null

        if (clientId == null) {
            throw new TenantNotFoundException("clientId is null");
        }

        // Stub: all clients resolve to default tenant
        return new TenantContext(DEFAULT_ORG_ID, DEFAULT_SPACE_ID);
    }
}
