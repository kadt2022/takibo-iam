package com.takibo.identitycore.application.auth.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Identité humaine vérifiée, prête à recevoir la preuve.
 * <p>
 * Tous les identifiants sont réels : TIS-CORE ne transmet cette demande au port
 * {@code HumanAccessTokenIssuer} qu'après avoir vérifié l'identité. Le sujet est HUMAN,
 * la méthode PASSWORD — par construction. Deux portées existent (IAM 31) :
 * <ul>
 *   <li><b>SPACE</b> — space et user local vérifiés : {@code spaceId} et {@code userId}
 *       présents ;</li>
 *   <li><b>ORGANIZATION</b> — le compte est identifié par son organisation seule :
 *       {@code spaceId} et {@code userId} absents, car le user local est une réalité
 *       de space. Une autorité d'organisation ne dépend d'aucun space.</li>
 * </ul>
 * {@code roles}/{@code groups}/{@code permissions} forment le snapshot borné du pouvoir
 * effectif (calculé par TIS-CORE) — TAS les signe sans jamais requêter le RBAC.
 */
public record HumanTokenRequest(
        UUID orgId,
        UUID spaceId,
        UUID accountId,
        UUID userId,
        List<String> roles,
        List<String> groups,
        List<String> permissions
) {
    public HumanTokenRequest {
        Objects.requireNonNull(orgId, "orgId is required");
        Objects.requireNonNull(accountId, "accountId is required");
        if ((spaceId == null) != (userId == null)) {
            throw new IllegalArgumentException(
                    "spaceId and userId must be both present (SPACE scope)"
                            + " or both absent (ORGANIZATION scope)");
        }
        roles = roles == null ? List.of() : List.copyOf(roles);
        groups = groups == null ? List.of() : List.copyOf(groups);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public static HumanTokenRequest spaceScoped(UUID orgId, UUID spaceId, UUID accountId,
                                                UUID userId, List<String> roles,
                                                List<String> groups, List<String> permissions) {
        Objects.requireNonNull(spaceId, "spaceId is required for a SPACE-scoped token");
        Objects.requireNonNull(userId, "userId is required for a SPACE-scoped token");
        return new HumanTokenRequest(orgId, spaceId, accountId, userId, roles, groups, permissions);
    }

    public static HumanTokenRequest organizationScoped(UUID orgId, UUID accountId,
                                                       List<String> roles, List<String> groups,
                                                       List<String> permissions) {
        return new HumanTokenRequest(orgId, null, accountId, null, roles, groups, permissions);
    }

    public boolean isOrganizationScoped() {
        return spaceId == null;
    }
}
