package com.takibo.managementservice.infrastructure.jpa.repository;

import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaSpaceRepository extends JpaRepository<SpaceEntity, UUID> {

    // Résolution toujours scopée à l'organisation : un space.code n'est unique que dans une org.
    // (Les anciennes méthodes globales findByCode/existsByCode ont été retirées : ambiguës et inutilisées.)
    boolean existsByOrgIdAndCode(UUID orgId, String code);

    Optional<SpaceEntity> findByOrgIdAndCode(UUID orgId, String code);

    /**
     * Retourne l'orgId d'un space à partir de son id.
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
