package com.takibo.managementservice.domain.repository;

import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.model.SpaceStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SpaceRepository {

    Optional<SpaceStatus> findStatusById(UUID id);

    Optional<Instant> findStatusUpdatedAtById(UUID id);

    /**
     * @return nombre de lignes mises à jour (0 si aucune, 1 si OK).
     */
    int updateStatus(UUID id, SpaceStatus status, String reason, Instant updatedAt);

    Space save(Space space);

    boolean existsByOrgIdAndCode(UUID orgId, String code);

    Optional<Space> findByOrgIdAndCode(UUID orgId, String code);
}
