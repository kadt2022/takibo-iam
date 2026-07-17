package com.takibo.managementservice.infrastructure.jpa.repository;

import com.takibo.managementservice.infrastructure.entity.OAuth2ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OAuth2ClientJpaRepository extends JpaRepository<OAuth2ClientEntity, UUID> {
    boolean existsByClientId(String clientId);
    Optional<OAuth2ClientEntity> findByIdAndOrgIdAndSpaceId(UUID id, UUID orgId, UUID spaceId);

    // Résumé organisationnel : compteur direct de TOUS les clients persistés dans
    // les Spaces de l'organisation (org_id est porté par chaque ligne — aucun
    // parcours des Spaces, aucune liste chargée, aucune autre organisation).
    long countByOrgId(UUID orgId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OAuth2ClientEntity c
               set c.clientSecretHash = :secretHash,
                   c.clientSecretExpiresAt = :expiresAt,
                   c.version = c.version + 1
             where c.id = :id
               and c.orgId = :orgId
               and c.spaceId = :spaceId
               and c.version = :expectedVersion
            """)
    int updateSecretByIdAndOrgIdAndSpaceId(@Param("id") UUID id,
                                           @Param("orgId") UUID orgId,
                                           @Param("spaceId") UUID spaceId,
                                           @Param("expectedVersion") Long expectedVersion,
                                           @Param("secretHash") String secretHash,
                                           @Param("expiresAt") Instant expiresAt);
}
