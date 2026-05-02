package com.takibo.managementservice.infrastructure.jpa.repository;

import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaSpaceRepository extends JpaRepository<SpaceEntity, UUID> {

    Optional<SpaceEntity> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByOrgIdAndCode(UUID orgId, String code);

    Optional<SpaceEntity> findByOrgIdAndCode(UUID orgId, String code);

    /**
     * Retourne l'orgId d'un port à partir de son id.
     * Portable MySQL/Postgres : aucun appel à UUID_TO_BIN/BIN_TO_UUID.
     */
    @Query("""
            select s.orgId
            from SpaceEntity s
            where s.id = :spaceId
            """)
    Optional<UUID> findOrgIdById(@Param("spaceId") UUID spaceId);

    int countByOrgId(UUID orgId);
}
