package com.takibo.identitycore.infrastructure.entity;

import com.takibo.identitycore.domain.model.GroupNature;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
        name = "\"groups\"",  // Mot réservé SQL - échapper avec quotes
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_groups_org_space_code", columnNames = {"org_id", "space_id", "code"}),
                @UniqueConstraint(name = "uk_groups_scope_id", columnNames = {"org_id", "space_id", "id"})
        },
        indexes = {
                @Index(name = "idx_groups_org_space", columnList = "org_id,space_id")
        }
)
public class GroupEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false, length = 20)
    private GroupNature nature;

    @Column(name = "code", nullable = false, length = 120)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
