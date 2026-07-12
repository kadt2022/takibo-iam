package com.takibo.identitycore.application.rbac.effective.port.in;

import com.takibo.identitycore.application.rbac.effective.model.EffectiveRbac;

import java.util.UUID;

/**
 * Calcul du RBAC effectif d'un account dans un space : rôles directs, groupes
 * directs, rôles hérités par les groupes, permissions dérivées des rôles
 * techniques effectifs. TIS-CORE est la seule vérité RBAC — TAS ne fait que
 * signer le résultat.
 */
public interface EffectiveRbacQueryCase {

    EffectiveRbac effectiveFor(UUID orgId, UUID spaceId, UUID accountId);

    /**
     * IAM 31 — pouvoir effectif de portée ORGANIZATION : uniquement les
     * attributions org-level (space_id NULL) et uniquement les codes de scope
     * ORGANIZATION. Jamais l'agrégation des pouvoirs de spaces — une autorité
     * d'organisation ne dépend d'aucun space.
     */
    EffectiveRbac effectiveOrgFor(UUID orgId, UUID accountId);
}
