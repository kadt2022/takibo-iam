package com.takibo.authorizationserver.infrastructure.keys;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.takibo.authorizationserver.domain.keys.model.KeyStatus;
import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;
import com.takibo.authorizationserver.domain.keys.model.TasSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter;
import com.takibo.authorizationserver.infrastructure.jpa.entity.TasSigningKeyEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.TasSigningKeyJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur JPA de la lecture ({@link SigningKeyRepository}) et de la rotation
 * ({@link SigningKeyWriter}) des clés de signature.
 * <p>
 * Un seul adaptateur pour les deux ports : ils portent sur la même table, et rien dans leur
 * traduction entité-domaine ne diffère selon qu'on lit ou qu'on écrit. Les règles de sélection
 * vivent dans les requêtes, écrites une seule fois ; celles d'activation vivent dans
 * {@code retireCurrentPlatformIssuer}, commentée là où elle est définie. Le jour où les clés
 * viendront d'un KMS, seul cet adaptateur change.
 * <p>
 * La matière privée chiffrée traverse telle quelle dans les deux sens : ni la déchiffrer ni la
 * chiffrer n'est le rôle de cet adaptateur — {@code PersistentJwkSource} fait la première,
 * {@link com.takibo.authorizationserver.domain.keys.SigningKeyRotationService} la seconde.
 */
@Repository
@Transactional(readOnly = true)
public class JpaSigningKeyRepository implements SigningKeyRepository, SigningKeyWriter {

    private final TasSigningKeyJpaRepository keys;

    public JpaSigningKeyRepository(TasSigningKeyJpaRepository keys) {
        this.keys = keys;
    }

    @Override
    public Optional<TasSigningKey> findActivePlatformIssuer(Instant at) {
        return keys.findActivePlatformIssuer(atOffset(at)).map(JpaSigningKeyRepository::toDomain);
    }

    @Override
    public boolean hasPlatformKeyHistory() {
        return keys.existsPlatformKey();
    }

    @Override
    public List<TasSigningKey> findPublishable(Instant at) {
        return keys.findPublishable(atOffset(at)).stream()
                .map(JpaSigningKeyRepository::toDomain)
                .toList();
    }

    /**
     * Insertion seule : aucune émettrice ne doit exister avant cet appel. Si une existe déjà,
     * cette insertion porte {@code is_issuer = TRUE} et {@code status = ACTIVE}, donc l'index
     * unique partiel de plateforme la refuse — sans vérification préalable, qui laisserait une
     * fenêtre de course entre la lecture et l'écriture.
     */
    @Override
    @Transactional
    public void activateFirstIssuer(NewSigningKey newKey) {
        if (newKey == null) {
            throw new IllegalArgumentException("SIGNING_KEY_ACTIVATION_REQUIRES_A_NEW_KEY");
        }
        keys.save(newIssuerEntity(newKey));
    }

    /**
     * Insertion arbitree par la base : voir
     * {@link SigningKeyWriter#tryActivateFirstIssuer} pour pourquoi l'unicite n'est pas
     * verifiee ici, et {@code TasSigningKeyJpaRepository#insertFirstPlatformIssuerIfAbsent}
     * pour la forme exacte du conflit infere.
     * <p>
     * Une transaction propre a cet appel : c'est elle qui rend la course observable du dehors
     * — l'insertion en conflit attend la fin de la transaction concurrente, puis rend zero, et
     * la ligne gagnante est alors visible pour la relecture de l'appelant.
     */
    @Override
    @Transactional
    public boolean tryActivateFirstIssuer(NewSigningKey candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("SIGNING_KEY_ACTIVATION_REQUIRES_A_NEW_KEY");
        }
        int inserted = keys.insertFirstPlatformIssuerIfAbsent(
                UUID.randomUUID(),
                candidate.kid(),
                candidate.alg(),
                candidate.kty(),
                candidate.keyUse(),
                serialize(candidate.publicJwkJson()),
                candidate.privateKeyEncrypted());
        return inserted == 1;
    }

    /**
     * La colonne est un {@code jsonb} et la requete est native : la forme publique doit donc
     * traverser en texte, la ou l'entite JPA laissait Hibernate s'en charger.
     * <p>
     * Serialisee par Nimbus, et non par un {@code ObjectMapper} injecte : le contexte en
     * expose plusieurs — dont celui, specialement configure, du store d'autorisations — et
     * en choisir un par autowiring ferait dependre la forme du JWK persiste d'un arbitrage
     * sans rapport. Nimbus est de toute facon la source de cette carte.
     */
    private static String serialize(Map<String, Object> publicJwkJson) {
        return JSONObjectUtils.toJSONString(publicJwkJson);
    }

    /**
     * Retirer puis activer dans la même transaction : c'est ce qui garantit qu'aucun instant
     * n'existe où ni l'ancienne ni la nouvelle clé ne signe, et que l'échec de l'une des deux
     * opérations annule l'autre plutôt que de laisser l'installation à moitié tournée.
     */
    @Override
    @Transactional
    public void activateNewIssuer(NewSigningKey newKey, Instant retiredKeyExpiresAt) {
        if (newKey == null) {
            throw new IllegalArgumentException("SIGNING_KEY_ACTIVATION_REQUIRES_A_NEW_KEY");
        }
        OffsetDateTime publishUntil = atOffset(retiredKeyExpiresAt);

        int retired = keys.retireCurrentPlatformIssuer(publishUntil);
        if (retired == 0) {
            // Rien à retirer : cet appel suppose une émettrice existante. L'amorçage d'une
            // installation vide passe par activateFirstIssuer, jamais par ici — le confondre
            // aurait pour seul signal un chevauchement inutilement exigé pour rien.
            throw new IllegalStateException(
                    "SIGNING_KEY_ROTATION_REQUIRES_AN_EXISTING_ACTIVE_ISSUER");
        }
        if (retired > 1) {
            // Ne devrait jamais arriver : l'index unique partiel garantit au plus une
            // émettrice de plateforme active à la fois. Si cette invariante était un jour
            // violée par ailleurs, mieux vaut l'échec net ici qu'une rotation qui continue
            // sur une base déjà incohérente.
            throw new IllegalStateException(
                    "MORE_THAN_ONE_PLATFORM_ISSUER_WAS_RETIRED: " + retired);
        }

        keys.save(newIssuerEntity(newKey));
    }

    private static TasSigningKeyEntity newIssuerEntity(NewSigningKey newKey) {
        return TasSigningKeyEntity.builder()
                .id(UUID.randomUUID())
                .orgId(null)
                .kid(newKey.kid())
                .alg(newKey.alg())
                .kty(newKey.kty())
                .keyUse(newKey.keyUse())
                .issuer(true)
                .status(KeyStatus.ACTIVE)
                .publicJwkJson(newKey.publicJwkJson())
                .privateKeyEncrypted(newKey.privateKeyEncrypted())
                .build();
    }

    private static OffsetDateTime atOffset(Instant at) {
        if (at == null) {
            throw new IllegalArgumentException("SIGNING_KEY_LOOKUP_REQUIRES_AN_INSTANT");
        }
        return at.atOffset(ZoneOffset.UTC);
    }

    private static TasSigningKey toDomain(TasSigningKeyEntity entity) {
        return new TasSigningKey(
                entity.getId(),
                entity.getOrgId(),
                entity.getKid(),
                entity.getAlg(),
                entity.getKty(),
                entity.getKeyUse(),
                entity.isIssuer(),
                entity.getStatus(),
                entity.getPublicJwkJson(),
                entity.getPrivateKeyEncrypted(),
                entity.getNotBefore(),
                entity.getExpiresAt(),
                entity.getPublishUntil(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
