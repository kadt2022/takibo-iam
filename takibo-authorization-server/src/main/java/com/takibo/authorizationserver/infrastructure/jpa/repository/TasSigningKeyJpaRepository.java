package com.takibo.authorizationserver.infrastructure.jpa.repository;

import com.takibo.authorizationserver.infrastructure.jpa.entity.TasSigningKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecture des cles de signature de plateforme.
 * <p>
 * {@code not_before} et {@code expires_at} bornent la periode de validite d'une cle
 * (cryptoperiode) ; {@code publish_until} borne separement la fin de publication d'une cle
 * retiree. Une borne absente signifie « pas de borne » — non « borne a maintenant ».
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
     * <p>
     * La borne haute est {@code publish_until}, pas {@code expires_at} : une cle ACTIVE n'a
     * pas de {@code publish_until} et reste donc toujours publiable, une cle RETIRED cesse de
     * l'etre une fois ce delai depasse, independamment de toute cryptoperiode.
     */
    @Query("""
            select k from TasSigningKeyEntity k
             where k.orgId is null
               and k.status <> com.takibo.authorizationserver.domain.keys.model.KeyStatus.REVOKED
               and (k.notBefore is null or k.notBefore <= :at)
               and (k.publishUntil is null or k.publishUntil > :at)
             order by k.issuer desc, k.kid asc
            """)
    List<TasSigningKeyEntity> findPublishable(@Param("at") OffsetDateTime at);

    /**
     * Retire l'émettrice de plateforme actuellement active, s'il y en a une : son statut passe
     * à {@code RETIRED} et sa fin de publication à {@code publishUntil}. Un appel sans
     * émettrice active affecte zéro ligne ; c'est à l'appelant — voir
     * {@code JpaSigningKeyRepository#activateNewIssuer} — de refuser ce cas, pas à cette
     * requête de le déguiser en amorçage.
     * <p>
     * Volontairement une mise à jour "aveugle" par condition, jamais par identifiant capturé
     * au préalable — mais cela ne suffit pas à sérialiser deux appels concurrents à soi seul.
     * Sous PostgreSQL, un {@code UPDATE} bloqué par le verrou de ligne d'un autre en cours ne
     * revalide, une fois débloqué, que <b>la ligne qu'il avait initialement ciblée</b> ; il ne
     * rebalaie pas la table pour découvrir une ligne insérée entre-temps par le concurrent. Si
     * ce dernier a déjà activé sa propre émettrice au moment du déblocage, cet appel-ci ne la
     * voit pas, ne la retire pas, puis tente d'insérer la sienne — et c'est l'
     * <b>index unique partiel</b> sur l'émettrice active de plateforme qui refuse alors cette
     * seconde insertion. Un appel concurrent peut donc échouer légitimement ; c'est
     * l'invariant — jamais deux émettrices actives — qui est garanti, pas l'absence
     * d'échec. Un appelant qui perd cette course doit relancer la rotation.
     */
    @Modifying
    @Query("""
            update TasSigningKeyEntity k
               set k.status = com.takibo.authorizationserver.domain.keys.model.KeyStatus.RETIRED,
                   k.publishUntil = :publishUntil
             where k.orgId is null
               and k.issuer = true
               and k.status = com.takibo.authorizationserver.domain.keys.model.KeyStatus.ACTIVE
            """)
    int retireCurrentPlatformIssuer(@Param("publishUntil") OffsetDateTime publishUntil);

    /**
     * Existe-t-il la moindre cle de plateforme, quel que soit son statut et sans borne
     * temporelle ? Voir {@code SigningKeyRepository#hasPlatformKeyHistory} pour ce que cette
     * question separe.
     */
    @Query("""
            select count(k) > 0 from TasSigningKeyEntity k
             where k.orgId is null
            """)
    boolean existsPlatformKey();

    /**
     * Insere la premiere emettrice de plateforme, ou ne fait rien si une autre transaction a
     * gagne la course.
     * <p>
     * Requete native, et non un {@code save()} JPA : l'arbitrage doit appartenir a PostgreSQL.
     * Un {@code save()} ne decouvrirait le conflit qu'au flush, sous la forme d'une violation
     * d'unicite qui laisse la transaction en rollback-only — la relecture de l'emettrice
     * gagnante y echouerait alors pour une raison sans rapport avec le probleme reel.
     * <p>
     * L'inference vise exactement l'index partiel {@code uk_tas_sk_platform_issuer_active} :
     * meme expression indexee, meme predicat. Un {@code ON CONFLICT DO NOTHING} generique
     * avalerait aussi une collision de {@code kid} ou toute autre anomalie, qui doivent rester
     * des echecs bruyants.
     *
     * @return 1 si cette insertion a active la cle, 0 si une emettrice concurrente etait la
     */
    @Modifying
    @Query(value = """
            insert into tas_signing_keys
                   (id, org_id, kid, alg, kty, key_use, is_issuer, status, public_jwk_json,
                    private_key_encrypted)
            values (:id, null, :kid, :alg, :kty, :keyUse, true, 'ACTIVE',
                    cast(:publicJwkJson as jsonb), :privateKeyEncrypted)
            on conflict ((org_id is null))
                 where is_issuer = true and status = 'ACTIVE' and org_id is null
            do nothing
            """, nativeQuery = true)
    int insertFirstPlatformIssuerIfAbsent(@Param("id") UUID id,
                                          @Param("kid") String kid,
                                          @Param("alg") String alg,
                                          @Param("kty") String kty,
                                          @Param("keyUse") String keyUse,
                                          @Param("publicJwkJson") String publicJwkJson,
                                          @Param("privateKeyEncrypted") String privateKeyEncrypted);
}
