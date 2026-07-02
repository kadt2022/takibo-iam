package com.takibo.identitycore.integration.security.port;

import java.util.UUID;

/**
 * Space porté par le token courant. Un token SPACE reste strictement limité au
 * {@code space_id} qu'il porte — aucun rôle ne transforme un token SPACE en token ORG.
 */
public interface CurrentSpaceContextCase {

    /**
     * @throws org.springframework.security.access.AccessDeniedException if no space context is available
     */
    UUID requireCurrentSpaceId();
}
