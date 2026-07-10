package com.takibo.managementservice.domain.exception;

import java.util.UUID;

/**
 * Space introuvable dans l'organisation ciblée. Le message ne révèle jamais
 * qu'un space existe dans une autre organisation (anti-énumération) : les
 * identifiants restent des propriétés internes, hors message HTTP.
 */
public class SpaceNotFoundException extends RuntimeException {

    private final UUID orgId;
    private final UUID spaceId;

    public SpaceNotFoundException(UUID orgId, UUID spaceId) {
        super("Space not found in organization");
        this.orgId = orgId;
        this.spaceId = spaceId;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getSpaceId() {
        return spaceId;
    }
}
