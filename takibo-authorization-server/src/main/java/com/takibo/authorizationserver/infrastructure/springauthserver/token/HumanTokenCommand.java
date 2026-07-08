package com.takibo.authorizationserver.infrastructure.springauthserver.token;

import java.util.List;
import java.util.UUID;

/**
 * Commande de signature d'un token humain situé.
 * <p>
 * Contrat neutre côté TAS : l'identité a déjà été vérifiée en amont (TIS-CORE) et TAS ne
 * connaît ni Account ni User — uniquement des identifiants réels et le snapshot borné du
 * pouvoir effectif (roles/groups/permissions), calculé par TIS-CORE. TAS ne requête
 * jamais le RBAC : il signe.
 */
public record HumanTokenCommand(
        UUID orgId,
        UUID spaceId,
        UUID accountId,
        UUID userId,
        List<String> roles,
        List<String> groups,
        List<String> permissions
) {
    public HumanTokenCommand {
        roles = roles == null ? List.of() : List.copyOf(roles);
        groups = groups == null ? List.of() : List.copyOf(groups);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
