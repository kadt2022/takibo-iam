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
        name = "group_roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gr_org_space_group_role", columnNames = {"org_id", "space_id", "group_id", "role_id"})
        },
        indexes = {
                @Index(name = "idx_gr_org_space_group", columnList = "org_id,space_id,group_id")
        }
)
public class GroupRoleEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by")
    private UUID assignedBy;
    
    @PrePersist
    void prePersist() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }

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
            @JoinColumn(name = "group_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private GroupEntity group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "org_id", referencedColumnName = "org_id", insertable = false, updatable = false),
            @JoinColumn(name = "space_id", referencedColumnName = "space_id", insertable = false, updatable = false),
            @JoinColumn(name = "role_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private RoleEntity role;
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupRoleEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
