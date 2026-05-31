package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.infrastructure.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaRoleRepository extends JpaRepository<RoleEntity, UUID> {

    @Query("""
        select (count(r) > 0)
        from RoleEntity r
        where r.spaceId = :spaceId
          and r.code = :code
        """)
    boolean existsBySpaceIdAndCode(@Param("spaceId") UUID spaceId,
                                   @Param("code") String code);

    @Query("""
        select r.id
        from RoleEntity r
        where r.spaceId = :spaceId
          and r.code = :code
        """)
    Optional<UUID> findIdBySpaceIdAndCode(@Param("spaceId") UUID spaceId,
                                          @Param("code") String code);

    List<RoleEntity> findBySpaceIdAndCodeIn(UUID spaceId, Collection<String> codes);

    List<RoleEntity> findByOrgIdAndSpaceIdAndCodeIn(UUID orgId, UUID spaceId, Collection<String> codes);

    Optional<RoleEntity> findBySpaceIdAndCode(UUID spaceId, String code);
}
