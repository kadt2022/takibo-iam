package com.takibo.identitycore.domain.rbac.repository;

import java.util.UUID;

/**
 * SEC-HUNT-06 — lecture et sérialisation de l'administration effective d'un Space.
 * <p>
 * Administrateur effectif : identité dont le User de ce Space est {@code ACTIVE} et qui détient
 * {@code R_SPACE_ADMIN}, directement ou par {@code G_SPACE_ADMINS}.
 */
public interface SpaceAdministrationRepository {

    /**
     * Sérialise, jusqu'à la fin de la transaction courante, toutes les décisions qui retirent un
     * pouvoir d'administration dans ce Space. Exige une transaction ouverte.
     */
    void lockAdministrationOf(UUID spaceId);

    /** Vrai si l'Account est un administrateur effectif du Space. */
    boolean isEffectiveAdministrator(UUID orgId, UUID spaceId, UUID accountId);

    /** Administrateurs effectifs du Space, l'Account donné exclu. */
    long countEffectiveAdministratorsOtherThan(UUID orgId, UUID spaceId, UUID accountId);
}
