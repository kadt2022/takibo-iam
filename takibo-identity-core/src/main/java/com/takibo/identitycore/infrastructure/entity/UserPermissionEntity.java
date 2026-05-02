package com.takibo.identitycore.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
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
        name = "user_permissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_up_org_space_user_perm", columnNames = {"org_id", "space_id", "user_id", "permission_id"})
        },
        indexes = {
                @Index(name = "idx_up_org_space_user", columnList = "org_id,space_id,user_id")
        }
)
public class UserPermissionEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "permission_id", nullable = false, updatable = false)
    private UUID permissionId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "effect", nullable = false, length = 8)
    private PermissionEffect effect = PermissionEffect.ALLOW;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "org_id", referencedColumnName = "org_id", insertable = false, updatable = false),
            @JoinColumn(name = "space_id", referencedColumnName = "space_id", insertable = false, updatable = false),
            @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "org_id", referencedColumnName = "org_id", insertable = false, updatable = false),
            @JoinColumn(name = "space_id", referencedColumnName = "space_id", insertable = false, updatable = false),
            @JoinColumn(name = "permission_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private PermissionEntity permission;
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPermissionEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
