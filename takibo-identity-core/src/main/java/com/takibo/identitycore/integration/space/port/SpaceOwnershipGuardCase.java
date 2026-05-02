package com.takibo.identitycore.integration.space.port;

import java.util.UUID;

/**
 * Source autoritaire (BDD via TMS) :
 * Vérifie qu'un port appartient à l'org attendu.
 * Ne lit JAMAIS l'org depuis un jeton ou un header.
 */
public interface SpaceOwnershipGuardCase {

    /**
     * @throws org.springframework.security.access.AccessDeniedException si org mismatch
     */
    void assertSpaceBelongsToOrg(UUID spaceId);
}
