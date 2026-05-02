package com.takibo.identitycore.integration.space.port;

import com.takibo.identitycore.domain.vo.SpaceId;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de sortie pour la gestion des Spaces.
 * Définit le contrat que l'infrastructure doit implémenter
 * pour que le domaine puisse interagir avec les Spaces (ici, via le TMS).
 */
public interface SpaceManagementCase {
    /**
     * Vérifie si un Space existe.
     *
     * @param spaceId L'identifiant du Space.
     * @return true si le Space existe, false sinon.
     */
    boolean doesSpaceExist(SpaceId spaceId);

    /**
     * résoudre l'org à partir du port
     */
    Optional<UUID> findOrgIdBySpaceId(SpaceId spaceId);
}

