package com.takibo.identitycore.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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
        name = "client_role_permissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_crp_org_space_client_role_perm", 
                                columnNames = {"org_id", "space_id", "client_id", "role_id", "permission_id"})
        },
        indexes = {
                @Index(name = "idx_crp_org_space_client", columnList = "org_id,space_id,client_id")
        }
)
public class ClientRolePermissionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false, updatable = false)
    private UUID permissionId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "effect", length = 8, nullable = false)
    private PermissionEffect effect = PermissionEffect.ALLOW;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "org_id", referencedColumnName = "org_id", insertable = false, updatable = false),
            @JoinColumn(name = "space_id", referencedColumnName = "space_id", insertable = false, updatable = false),
            @JoinColumn(name = "role_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private RoleEntity role;

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
        if (!(o instanceof ClientRolePermissionEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
