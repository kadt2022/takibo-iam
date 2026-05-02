package com.takibo.identitycore.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "account_credentials",
        indexes = {
                @Index(name = "idx_acctcred_org", columnList = "org_id"),
                @Index(name = "idx_acctcred_org_locked_until", columnList = "org_id,locked_until")
        }
)
@IdClass(AccountCredentialsEntity.AccountCredentialsId.class)
public class AccountCredentialsEntity implements Persistable<AccountCredentialsEntity.AccountCredentialsId> {

    @Id
    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /** Relation propriétaire avec clé partagée (PK=FK) */
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "account_id")
    private AccountEntity account;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_algo", length = 40)
    private String passwordAlgo;

    @Column(name = "password_version")
    private Integer passwordVersion;

    @Column(name = "password_updated_at")
    private Instant passwordUpdatedAt;

    @Builder.Default
    @Column(name = "must_change_next_login", nullable = false)
    private boolean mustChangeNextLogin = false;

    @Builder.Default
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder.Default
    @Transient
    private boolean isNew = true;

    @Override
    public AccountCredentialsId getId() {
        return new AccountCredentialsId(orgId, accountId);
    }

    @Override
    public boolean isNew() {
        return isNew || version == null;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @PrePersist
    void prePersistDefaults() {
        if (passwordUpdatedAt == null) {
            passwordUpdatedAt = Instant.now();
        }
    }

    // PK composite => equals/hashCode sur les 2 colonnes
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountCredentialsEntity that)) return false;
        return Objects.equals(orgId, that.orgId) && Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, accountId);
    }

    // Composite Key Class
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class AccountCredentialsId implements Serializable {
        private UUID orgId;
        private UUID accountId;
    }
}
