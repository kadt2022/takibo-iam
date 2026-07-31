package com.takibo.authorizationserver.infrastructure.springauthserver.token;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Commande de signature d'un token humain situé.
 * <p>
 * Contrat neutre côté TAS : l'identité a déjà été vérifiée en amont (TIS-CORE) et TAS ne
 * connaît ni Account ni User — uniquement des identifiants réels et le snapshot borné du
 * pouvoir effectif (roles/groups/permissions), calculé par TIS-CORE. TAS ne requête
 * jamais le RBAC : il vérifie la cohérence de plan puis signe. La provenance du contexte
 * ({@code tenantSource}) est elle aussi décidée par TIS-CORE.
 */
public record HumanTokenCommand(
        UUID orgId,
        UUID spaceId,
        UUID accountId,
        UUID userId,
        String tenantSource,
        List<String> roles,
        List<String> groups,
        List<String> permissions
) {
    public HumanTokenCommand {
        Objects.requireNonNull(tenantSource, "tenantSource is required");
        if (tenantSource.isBlank()) {
            throw new IllegalArgumentException("tenantSource must not be blank");
        }
        roles = roles == null ? List.of() : List.copyOf(roles);
        groups = groups == null ? List.of() : List.copyOf(groups);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
