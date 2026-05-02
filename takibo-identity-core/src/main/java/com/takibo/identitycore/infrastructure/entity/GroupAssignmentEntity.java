package com.takibo.identitycore.infrastructure.entity;

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
        name = "group_assignments",
        indexes = {
                @Index(name = "idx_ga_identity", columnList = "org_id, identity_type, identity_id")
        }
)
public class GroupAssignmentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "space_id")
    private UUID spaceId;

    @Column(name = "identity_type", nullable = false, length = 30)
    private String identityType;

    @Column(name = "identity_id", nullable = false, updatable = false)
    private UUID identityId;

    @Column(name = "group_code", length = 120)
    private String groupCode;

    @Column(name = "business_group_id")
    private UUID businessGroupId;

    @Builder.Default
    @Column(name = "group_source", nullable = false, length = 30)
    private String groupSource = "SYSTEM";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "org_id", referencedColumnName = "org_id", insertable = false, updatable = false),
            @JoinColumn(name = "identity_id", referencedColumnName = "identity_id", insertable = false, updatable = false)
    })
    private TakiboIdentityEntity identity;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupAssignmentEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
