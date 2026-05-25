package com.takibo.identitycore.integration.space.port;

import java.util.UUID;

public interface SpaceOwnershipGuardCase {

    /**
     * @throws org.springframework.security.access.AccessDeniedException if spaceId does not belong to expectedOrgId
     * @throws com.takibo.identitycore.domain.exception.SpaceNotFoundException if spaceId is unknown
     */
    void assertSpaceBelongsToOrg(UUID spaceId, UUID expectedOrgId);
}
