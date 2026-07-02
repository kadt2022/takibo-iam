package com.takibo.authorizationserver.infrastructure.springauthserver.token;

import java.util.List;
import java.util.UUID;

/**
 * Commande de signature d'un token humain situé.
 * <p>
 * Contrat neutre côté TAS : l'identité a déjà été vérifiée en amont (TIS-CORE) et TAS ne
 * connaît ni Account ni User — uniquement des identifiants réels et un snapshot de rôles.
 */
public record HumanTokenCommand(
        UUID orgId,
        UUID spaceId,
        UUID accountId,
        UUID userId,
        List<String> roles
) {
    public HumanTokenCommand {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
