package com.takibo.identitycore.domain.repository;

import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.vo.SpaceId;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {

    boolean existsBySpaceIdAndCode(SpaceId spaceId, String roleCode);

    Optional<UUID> findIdBySpaceIdAndCode(SpaceId spaceId, String roleCode);

    Optional<Role> findBySpaceIdAndCode(SpaceId spaceId, String code);

    /** Persiste et retourne l’agrégat sauvegardé */
    Role save(Role role);
}
