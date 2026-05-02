package com.takibo.identitycore.infrastructure.entity;

import com.takibo.identitycore.domain.model.IdentityType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "takibo_identities",
        indexes = {
                @Index(name = "idx_ti_org", columnList = "org_id"),
                @Index(name = "idx_ti_org_account", columnList = "org_id, account_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ti_org_account", columnNames = {"org_id", "account_id"}),
                @UniqueConstraint(name = "uk_ti_org_identity", columnNames = {"org_id", "identity_id"})
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TakiboIdentityEntity {

    @Id
    @Column(name = "identity_id", nullable = false, updatable = false)
    private UUID identityId;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_type", nullable = false, length = 32)
    private IdentityType identityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_status", nullable = false, length = 32)
    @Builder.Default
    private IdentityStatus identityStatus = IdentityStatus.ACTIVE;

    @Column(name = "trust_level", nullable = false)
    @Builder.Default
    private Integer trustLevel = 0;

    @Column(name = "risk_score", nullable = false)
    @Builder.Default
    private Integer riskScore = 0;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TakiboIdentityEntity that)) return false;
        return Objects.equals(identityId, that.identityId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(identityId);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (identityId == null) {
            identityId = UUID.randomUUID();
        }
        if (identityType == null) {
            identityType = IdentityType.HUMAN;
        }
        if (identityStatus == null) {
            identityStatus = IdentityStatus.ACTIVE;
        }
        if (trustLevel == null) {
            trustLevel = 0;
        }
        if (riskScore == null) {
            riskScore = 0;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}