package com.takibo.identitycore.integration.space.port;

import java.util.UUID;

/**
 * Résultat d'une résolution {@code orgCode -> orgId} (IAM 31 — login organisationnel).
 * <p>
 * Types neutres uniquement : aucune entité JPA du TMS ne franchit cette frontière.
 * Le code est retourné sous sa forme canonique (normalisée).
 */
public record ResolvedOrgKey(
        UUID orgId,
        String orgCode
) {}
