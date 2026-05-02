package com.takibo.managementservice.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Space Domain Entity - Domain verification for Spaces
 * Aligned with DDL: space_domains table
 * 
 * DDL: Composite FK (org_id, space_id) -> spaces(org_id, id)
 * DDL: UK (org_id, space_id, LOWER(domain))
 */
@Entity
@Table(
    name = "space_domains",
    indexes = {
        @Index(name = "idx_space_domains_org_space", columnList = "org_id, space_id")
    }
    // Note: UK avec LOWER(domain) géré par DDL unique index
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaceDomainEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    // ===== COMPOSITE FK TO SPACE =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "org_id", referencedColumnName = "org_id", 
                    insertable = false, updatable = false),
        @JoinColumn(name = "space_id", referencedColumnName = "id", 
                    insertable = false, updatable = false)
    })
    private SpaceEntity space;

    @Column(name = "domain", nullable = false, length = 255)
    private String domain;

    @Column(name = "verified", nullable = false)
    @Builder.Default
    private Boolean verified = false;

    @Column(name = "verification_token", length = 255)
    private String verificationToken;

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

    // ===== BUSINESS METHODS =====

    public void verify() {
        this.verified = true;
        this.verificationToken = null;
    }

    public void generateVerificationToken() {
        this.verificationToken = UUID.randomUUID().toString();
        this.verified = false;
    }

    // ===== EQUALS & HASHCODE =====

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpaceDomainEntity that)) return false;
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
        if (verified == null) {
            verified = false;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}
