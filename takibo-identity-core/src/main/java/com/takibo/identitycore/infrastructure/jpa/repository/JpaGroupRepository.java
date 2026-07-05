package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.infrastructure.entity.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaGroupRepository extends JpaRepository<GroupEntity, UUID> {

    @Query("select (count(g) > 0) from GroupEntity g where g.spaceId = :spaceId and g.code = :code")
    boolean existsBySpaceIdAndCode(@Param("spaceId") UUID spaceId, @Param("code") String code);

    @Query("select g.id from GroupEntity g where g.spaceId = :spaceId and g.code = :code")
    Optional<UUID> findIdBySpaceIdAndCode(@Param("spaceId") UUID spaceId, @Param("code") String code);

    List<GroupEntity> findBySpaceIdAndCodeIn(UUID spaceId, Collection<String> codes);

    Optional<GroupEntity> findBySpaceIdAndCode(UUID spaceId, String code);

    List<GroupEntity> findByOrgIdAndSpaceIdOrderByCodeAsc(UUID orgId, UUID spaceId);
}
