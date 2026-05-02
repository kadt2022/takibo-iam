package com.takibo.authorizationserver.infrastructure.jpa.entity;

import com.takibo.authorizationserver.domain.keys.model.KeyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "context_hmac_keys",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_context_hmac_scope_version", columnNames = {"org_id", "space_id", "key_version"})
        },
        indexes = {
                @Index(name = "idx_context_hmac_org_space", columnList = "org_id, space_id"),
                @Index(name = "idx_context_hmac_scope_status", columnList = "org_id, space_id, status")
        }
)
public class ContextHmacKeyEntity {

    @Id
    @Column(name = "key_id", nullable = false)
    private UUID keyId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(name = "key_value", nullable = false, length = 1024)
    private String keyValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private KeyStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "retired_at")
    private OffsetDateTime retiredAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoke_reason", length = 255)
    private String revokeReason;
}
