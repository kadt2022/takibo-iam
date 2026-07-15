package com.takibo.managementservice.infrastructure.jpa.repository;

import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaSpaceRepository extends JpaRepository<SpaceEntity, UUID> {

    // Deux variantes plutôt qu'un ":q is null or ..." : lier un paramètre String null
    // dans lower() fait inférer bytea à PostgreSQL (même idiome que JpaUserRepository).
    // :q arrive TOUJOURS non-null, déjà en %pattern% minuscule.
    String LIST_FILTERS = """
            where s.orgId = :orgId
              and (:status is null or s.status = :status)
            """;

    String SEARCH_FILTER = """
              and (lower(s.code) like :q or lower(s.name) like :q)
            """;

    @Query("select s from SpaceEntity s " + LIST_FILTERS)
    Page<SpaceEntity> findPageByOrg(@Param("orgId") UUID orgId,
                                    @Param("status") SpaceStatus status,
                                    Pageable pageable);

    @Query("select s from SpaceEntity s " + LIST_FILTERS + SEARCH_FILTER)
    Page<SpaceEntity> searchPageByOrg(@Param("orgId") UUID orgId,
                                      @Param("status") SpaceStatus status,
                                      @Param("q") String q,
                                      Pageable pageable);

    // Recherche toujours située : un space d'une autre org N'EXISTE PAS (404 anti-énumération).
    Optional<SpaceEntity> findByIdAndOrgId(UUID id, UUID orgId);

    // Résolution toujours scopée à l'organisation : un space.code n'est unique que dans une org.
    // (Les anciennes méthodes globales findByCode/existsByCode ont été retirées : ambiguës et inutilisées.)
    boolean existsByOrgIdAndCode(UUID orgId, String code);

    Optional<SpaceEntity> findByOrgIdAndCode(UUID orgId, String code);

    List<SpaceEntity> findByOrgIdAndIdIn(UUID orgId, Collection<UUID> ids);

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
