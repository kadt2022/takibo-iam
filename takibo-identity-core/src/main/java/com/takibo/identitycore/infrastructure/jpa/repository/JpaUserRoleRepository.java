package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.infrastructure.entity.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaUserRoleRepository extends JpaRepository<UserRoleEntity, UUID> {
    boolean existsBySpaceIdAndUserIdAndRoleId(UUID spaceId, UUID userId, UUID roleId);

    boolean existsByOrgIdAndSpaceIdAndUserIdAndRoleId(UUID orgId, UUID spaceId, UUID userId, UUID roleId);
}
