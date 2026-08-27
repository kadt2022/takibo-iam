package com.takibo.authorizationserver.infrastructure.jpa.entity;

import com.takibo.authorizationserver.domain.keys.model.KeyStatus;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "tas_signing_keys",
        // Le kid est unique globalement, et non par organisation : le JWKS est un endpoint
        // unique, deux cles homonymes y seraient indistinguables a la verification.
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tas_sk_kid_global", columnNames = {"kid"})
        },
        indexes = {
                @Index(name = "idx_tas_sk_org", columnList = "org_id"),
                @Index(name = "idx_tas_sk_org_status", columnList = "org_id, status"),
                @Index(name = "idx_tas_sk_org_expires", columnList = "org_id, expires_at")
        }
)
public class TasSigningKeyEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** {@code null} = clé de plateforme, seule portée utilisée tant que TAS est mono-tenant. */
    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "kid", nullable = false, length = 64)
    private String kid;

    @Column(name = "alg", nullable = false, length = 32)
    private String alg;

    @Column(name = "kty", nullable = false, length = 16)
    private String kty;

    @Column(name = "key_use", nullable = false, length = 16)
    private String keyUse;

    @Column(name = "is_issuer", nullable = false)
    private boolean issuer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private KeyStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "public_jwk_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> publicJwkJson;

    @Column(name = "private_key_encrypted", length = 8000)
    private String privateKeyEncrypted;

    @Column(name = "not_before")
    private OffsetDateTime notBefore;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
