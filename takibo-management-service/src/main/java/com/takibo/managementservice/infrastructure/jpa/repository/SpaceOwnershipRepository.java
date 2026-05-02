package com.takibo.managementservice.infrastructure.jpa.repository;

import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SpaceOwnershipRepository extends JpaRepository<SpaceEntity, UUID> {

    @Query("select s.orgId from SpaceEntity s where s.id = :spaceId")
    Optional<UUID> findOrgIdBySpaceId(UUID spaceId);
}
