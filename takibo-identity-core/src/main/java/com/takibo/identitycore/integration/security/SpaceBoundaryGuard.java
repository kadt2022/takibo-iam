package com.takibo.identitycore.integration.security;

import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
import com.takibo.identitycore.integration.security.port.CurrentSpaceContextCase;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Frontière stricte du token situé : {@code token.org_id == resolved.org_id} et
 * {@code token.space_id == resolved.space_id}. Aucun rôle — pas même R_ORG_OWNER —
 * n'élargit cette frontière ; le rôle autorise l'action, la frontière reste le token.
 */
@Component
@RequiredArgsConstructor
public class SpaceBoundaryGuard {

    private final CurrentOrganizationContextCase currentOrganizationContext;
    private final CurrentSpaceContextCase currentSpaceContext;

    public void assertTokenMatches(ResolvedSpaceKey key) {
        UUID currentOrgId = currentOrganizationContext.requireCurrentOrganizationId();
        if (!currentOrgId.equals(key.orgId())) {
            throw new AccessDeniedException("ORG_MISMATCH");
        }

        UUID currentSpaceId = currentSpaceContext.requireCurrentSpaceId();
        if (!currentSpaceId.equals(key.spaceId())) {
            throw new AccessDeniedException("SPACE_CONTEXT_MISMATCH");
        }
    }
}
