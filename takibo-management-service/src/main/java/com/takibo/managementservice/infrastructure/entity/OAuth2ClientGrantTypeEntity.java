package com.takibo.managementservice.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

/**
 * OAuth2 Client Grant Type Entity
 * Aligned with DDL: oauth2_client_grant_types table
 */
@Entity
@Table(
    name = "oauth2_client_grant_types",
    indexes = {
        @Index(name = "idx_ocg_client", columnList = "org_id, space_id, client_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ocg_client_grant", 
                         columnNames = {"org_id", "space_id", "client_id", "grant_type"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuth2ClientGrantTypeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "org_id", referencedColumnName = "org_id", 
                    insertable = false, updatable = false),
        @JoinColumn(name = "space_id", referencedColumnName = "space_id", 
                    insertable = false, updatable = false),
        @JoinColumn(name = "client_id", referencedColumnName = "id", 
                    insertable = false, updatable = false)
    })
    private OAuth2ClientEntity client;

    @Column(name = "grant_type", nullable = false, length = 64)
    private String grantType;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OAuth2ClientGrantTypeEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
