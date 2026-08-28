package com.takibo.authorizationserver.infrastructure.keys;

import com.takibo.authorizationserver.domain.keys.model.TasSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;
import com.takibo.authorizationserver.infrastructure.jpa.entity.TasSigningKeyEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.TasSigningKeyJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Adaptateur JPA du {@link SigningKeyRepository}.
 * <p>
 * Traduit l'entite en enregistrement de domaine, et rien de plus : aucune regle de selection
 * ici, elles vivent dans les requetes, ecrites une seule fois. Le jour ou les cles viendront
 * d'un KMS, seul cet adaptateur change.
 * <p>
 * La matiere privee chiffree traverse telle quelle : la dechiffrer est le role du
 * {@code JWKSource}, qui seul connait le contexte a fournir au chiffreur.
 */
@Repository
@Transactional(readOnly = true)
public class JpaSigningKeyRepository implements SigningKeyRepository {

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
