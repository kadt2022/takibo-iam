package com.takibo.managementservice.infrastructure.entity;

import com.takibo.managementservice.domain.model.SpaceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Space Entity - Workspace within Organization
 * Aligned with DDL: spaces table
 *
 * DDL Constraints:
 * - CONSTRAINT uk_spaces_org_code UNIQUE (org_id, code)
 * - CONSTRAINT uk_spaces_org_id UNIQUE (org_id, id)  -- For composite FKs
 */
@Entity
@Table(
        name = "spaces",
        indexes = {
                @Index(name = "idx_spaces_org", columnList = "org_id"),
                @Index(name = "idx_spaces_status", columnList = "org_id, status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_spaces_org_code", columnNames = {"org_id", "code"}),
                @UniqueConstraint(name = "uk_spaces_org_id", columnNames = {"org_id", "id"})
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaceEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SpaceStatus status = SpaceStatus.ACTIVE;

    @Column(name = "status_reason", length = 512)
    private String statusReason;

    @Column(name = "status_updated_at", nullable = false)
    @Builder.Default
    private Instant statusUpdatedAt = Instant.now();

    @Column(name = "owner_account_id")
    private UUID ownerAccountId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // ===== RELATIONS =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", insertable = false, updatable = false)
    private OrganizationEntity organization;

    @OneToMany(mappedBy = "space", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SpaceDomainEntity> domains = new ArrayList<>();

    @OneToMany(mappedBy = "space", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OAuth2ClientEntity> oAuth2Clients = new ArrayList<>();

    // ===== BUSINESS METHODS =====

    public void updateStatus(SpaceStatus newStatus, String reason) {
        this.status = newStatus;
        this.statusReason = reason;
        this.statusUpdatedAt = Instant.now();
    }

    public void addDomain(SpaceDomainEntity domain) {
        domains.add(domain);
        domain.setSpace(this);
    }

    public void removeDomain(SpaceDomainEntity domain) {
        domains.remove(domain);
        domain.setSpace(null);
    }

    // ===== EQUALS & HASHCODE =====

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpaceEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    // ===== PRE-PERSIST =====

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = SpaceStatus.ACTIVE;
        }
        if (statusUpdatedAt == null) {
            statusUpdatedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}