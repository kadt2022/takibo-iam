package com.takibo.identitycore.application.auth.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Identité humaine vérifiée, prête à recevoir la preuve.
 * <p>
 * Tous les identifiants sont réels et situés : TIS-CORE ne transmet cette demande au port
 * {@code HumanAccessTokenIssuer} qu'après avoir vérifié account, credentials et user local.
 * Le sujet est HUMAN, la méthode PASSWORD, la frontière SPACE — par construction.
 */
public record HumanTokenRequest(
        UUID orgId,
        UUID spaceId,
        UUID accountId,
        UUID userId,
        List<String> roles
) {
    public HumanTokenRequest {
        Objects.requireNonNull(orgId, "orgId is required");
        Objects.requireNonNull(spaceId, "spaceId is required");
        Objects.requireNonNull(accountId, "accountId is required");
        Objects.requireNonNull(userId, "userId is required");
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
