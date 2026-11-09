package com.takibo.identitycore.application.auth.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Un Account authentifié dans une Organization, et seulement dans celle-ci. Il ne porte aucun
 * secret ni aucun pouvoir : rôles, groupes et permissions restent calculés par TIS-Core au
 * moment où une preuve est émise.
 */
public record VerifiedHuman(UUID orgId, UUID accountId, AuthenticationMethod method, Instant authenticatedAt) {
}
