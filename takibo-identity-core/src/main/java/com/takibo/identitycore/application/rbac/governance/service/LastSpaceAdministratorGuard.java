package com.takibo.identitycore.application.rbac.governance.service;

import com.takibo.identitycore.domain.exception.LastAdminRemovalException;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.rbac.repository.SpaceAdministrationRepository;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * SEC-HUNT-06 — invariant : un Space garde toujours au moins un administrateur effectif.
 * <p>
 * Administrateur effectif : identité dont le User de ce Space est {@code ACTIVE} et qui détient
 * {@code R_SPACE_ADMIN}, directement ou par {@code G_SPACE_ADMINS}. Retirer l'une de ces deux
 * voies n'est refusé que si la cible cesse alors d'être administrateur et qu'aucun autre
 * administrateur effectif ne demeure.
 * <p>
 * Les retraits d'un même Space sont sérialisés avant toute lecture : deux administrateurs qui se
 * retirent mutuellement ce pouvoir au même instant ne peuvent plus réussir tous les deux. Doit
 * être appelé dans la transaction qui effectue la suppression.
 */
@Component
@RequiredArgsConstructor
class LastSpaceAdministratorGuard {

    private final SpaceAdministrationRepository administration;

    /**
     * @param targetKeepsAdministration vrai si la cible détient encore l'autre voie ; évalué
     *                                  après la prise du verrou
     */
    void assertSpaceKeepsAnAdministrator(ResolvedSpaceKey key, User target, String removed,
                                         BooleanSupplier targetKeepsAdministration) {
        administration.lockAdministrationOf(key.spaceId());
        if (target.getStatus() != UserStatus.ACTIVE || targetKeepsAdministration.getAsBoolean()) {
            return;
        }
        UUID targetAccountId = target.getAccountId().getValue();
        if (administration.countEffectiveAdministratorsOtherThan(key.orgId(), key.spaceId(), targetAccountId) == 0) {
            throw new LastAdminRemovalException(
                    "Cannot remove " + removed + ": the space would have no effective administrator left");
        }
    }
}
