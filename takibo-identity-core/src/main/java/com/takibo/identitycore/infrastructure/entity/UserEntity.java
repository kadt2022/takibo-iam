package com.takibo.identitycore.infrastructure.entity;

import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_org_space_account", columnNames = {"org_id", "space_id", "account_id"}),
                @UniqueConstraint(name = "uk_users_scope_id", columnNames = {"org_id", "space_id", "id"})
        },
        indexes = {
                @Index(name = "idx_users_org_space", columnList = "org_id,space_id"),
                @Index(name = "idx_users_org_space_status", columnList = "org_id,space_id,status")
        }
)
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    // PAS de FK JPA vers Space - juste l'UUID (géré par TMS)
    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    // Phase A: accountId = identityId
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    // FK vers takibo_identities(org_id, account_id) selon DDL
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "org_id", referencedColumnName = "org_id", insertable = false, updatable = false),
            @JoinColumn(name = "account_id", referencedColumnName = "account_id", insertable = false, updatable = false)
    })
    private TakiboIdentityEntity identity;

    // ✅ CORRECTION: Ajout de la relation vers Account pour accéder à l'email
    // Cette relation permet aux requêtes JPA d'utiliser "u.account.email"
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "id", insertable = false, updatable = false)
    private AccountEntity account;

    @Column(name = "username", nullable = false, length = 150)
    private String username;

    @Column(name = "first_name", length = 160)
    private String firstName;

    @Column(name = "last_name", length = 160)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status;

    // DDL CHECK: ('PLATFORM_ADMIN','SPACE_ADMIN','END_USER')
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 32)
    private UserType type;

    @Builder.Default
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    @Builder.Default
    @Column(name = "password_expired", nullable = false)
    private boolean passwordExpired = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void prePersistDefaults() {
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
        // type doit être fourni explicitement - pas de default
    }

    // PK simple => equals/hashCode sur id ONLY
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}