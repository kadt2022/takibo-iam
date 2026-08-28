package com.takibo.authorizationserver.infrastructure.keys;

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
    public List<TasSigningKey> findPublishable(Instant at) {
        return keys.findPublishable(atOffset(at)).stream()
                .map(JpaSigningKeyRepository::toDomain)
                .toList();
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
        OffsetDateTime expiresAt = atOffset(retiredKeyExpiresAt);

        keys.retireCurrentPlatformIssuer(expiresAt);

        TasSigningKeyEntity entity = TasSigningKeyEntity.builder()
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
        keys.save(entity);
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
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
