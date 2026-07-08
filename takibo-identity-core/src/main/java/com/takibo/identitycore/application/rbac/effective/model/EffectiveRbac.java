package com.takibo.identitycore.application.rbac.effective.model;

import java.util.List;

/**
 * Snapshot borné du pouvoir effectif d'un user dans un space, au moment de
 * l'authentification. Partition par provenance, jamais par préfixe :
 * {@code roles} ne contient que des codes issus d'assignations ou de liens de
 * rôles, {@code groups} que des codes issus de memberships, {@code permissions}
 * que des codes dérivés des rôles techniques effectifs.
 * <p>
 * Listes dédupliquées et triées — le token doit être déterministe.
 */
public record EffectiveRbac(
        List<String> roles,
        List<String> groups,
        List<String> permissions
) {
    public static final EffectiveRbac EMPTY = new EffectiveRbac(List.of(), List.of(), List.of());

    public EffectiveRbac {
        roles = roles == null ? List.of() : List.copyOf(roles);
        groups = groups == null ? List.of() : List.copyOf(groups);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
