package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.infrastructure.entity.GroupRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaGroupRolesRepository extends JpaRepository<GroupRoleEntity, UUID> {
    boolean existsBySpaceIdAndGroupIdAndRoleId(UUID spaceId, UUID groupId, UUID roleId);

    // variantes utiles si tu préfères
    Optional<GroupRoleEntity> findBySpaceIdAndGroupIdAndRoleId(UUID spaceId, UUID groupId, UUID roleId);

    // fallback explicite JPQL (équivalent à existsBy…)
    @Query("""
           select case when count(gr) > 0 then true else false end
           from GroupRoleEntity gr
           where gr.spaceId = :spaceId and gr.groupId = :groupId and gr.roleId = :roleId
           """)
    boolean existsTriple(@Param("spaceId") UUID spaceId,
                         @Param("groupId") UUID groupId,
                         @Param("roleId") UUID roleId);
}