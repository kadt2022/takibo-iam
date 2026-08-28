package com.takibo.authorizationserver.infrastructure.jpa.repository;

import com.takibo.authorizationserver.infrastructure.jpa.entity.TasSigningKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecture des cles de signature de plateforme.
 * <p>
 * Les deux requetes portent la meme fenetre temporelle, ecrite une fois ici plutot que
 * reconstituee par les appelants : {@code not_before} et {@code expires_at} sont facultatifs,
 * et une borne absente signifie « pas de borne » — non « borne a maintenant ».
 * <p>
 * {@code org_id IS NULL} restreint a la portee plateforme, seule utilisee tant que TAS reste
 * single-issuer. Des cles org-scopees peuvent coexister en base sans jamais etre servies ici.
 */
public interface TasSigningKeyJpaRepository extends JpaRepository<TasSigningKeyEntity, UUID> {

    @Query("""
            select k from TasSigningKeyEntity k
             where k.orgId is null
               and k.issuer = true
               and k.status = com.takibo.authorizationserver.domain.keys.model.KeyStatus.ACTIVE
               and (k.notBefore is null or k.notBefore <= :at)
               and (k.expiresAt is null or k.expiresAt > :at)
            """)
    Optional<TasSigningKeyEntity> findActivePlatformIssuer(@Param("at") OffsetDateTime at);

    /**
     * Actives et retirees, jamais revoquees. Ordonnees emettrice d'abord : le
     * {@code JWKSource} s'appuie sur cet ordre pour designer la cle de signature sans avoir a
     * retrier.
     */
    @Query("""
            select k from TasSigningKeyEntity k
             where k.orgId is null
               and k.status <> com.takibo.authorizationserver.domain.keys.model.KeyStatus.REVOKED
               and (k.notBefore is null or k.notBefore <= :at)
               and (k.expiresAt is null or k.expiresAt > :at)
             order by k.issuer desc, k.kid asc
            """)
    List<TasSigningKeyEntity> findPublishable(@Param("at") OffsetDateTime at);
}
