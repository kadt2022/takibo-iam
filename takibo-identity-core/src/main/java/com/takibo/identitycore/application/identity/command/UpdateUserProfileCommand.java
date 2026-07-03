package com.takibo.identitycore.application.identity.command;

import java.util.Map;
import java.util.UUID;

/**
 * Modification du profil LOCAL d'un user (PATCH : un champ null = inchangé).
 * Ne transporte jamais accountId/orgId/spaceId/email/roles/password.
 */
public record UpdateUserProfileCommand(
        UUID userId,
        String username,
        String firstName,
        String lastName,
        Map<String, Object> metadata
) {
}
