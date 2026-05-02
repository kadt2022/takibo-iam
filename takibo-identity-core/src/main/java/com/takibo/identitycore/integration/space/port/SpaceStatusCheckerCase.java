package com.takibo.identitycore.integration.space.port;

import com.takibo.identitycore.domain.exception.SpaceNotActiveException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.domain.status.SpaceOperationalStatus;

import java.util.Optional;
import java.util.UUID;

public interface SpaceStatusCheckerCase {

    /**
     * @return Optional.empty() si le port est inconnu/indisponible.
     */
    Optional<SpaceOperationalStatus> findStatus(UUID spaceId);

    /** Helper “binaire” conservé (fail-closed si inconnu) */
    default boolean isActive(UUID spaceId) {
        return findStatus(spaceId)
                .map(SpaceOperationalStatus.ACTIVE::equals)
                .orElse(false);
    }

    /** NEW: Lève 404 si inexistant, 403 si non ACTIVE. */
    default void assertSpacetExistsAndActive(UUID spaceId) {
        SpaceOperationalStatus status = findStatus(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));
        if (!SpaceOperationalStatus.ACTIVE.equals(status)) {
            throw new SpaceNotActiveException(spaceId);
        }
    }
}
