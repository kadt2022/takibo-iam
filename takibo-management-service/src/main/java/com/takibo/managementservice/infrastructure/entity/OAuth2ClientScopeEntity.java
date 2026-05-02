package com.takibo.managementservice.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

/**
 * OAuth2 Client Scope Entity
 * Aligned with DDL: oauth2_client_scopes table
 * 
 * DDL: Composite FK (org_id, space_id, client_id) -> oauth2_clients(org_id, space_id, id)
 */
@Entity
@Table(
    name = "oauth2_client_scopes",
    indexes = {
        @Index(name = "idx_ocs_client", columnList = "org_id, space_id, client_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ocs_client_scope", 
                         columnNames = {"org_id", "space_id", "client_id", "scope"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuth2ClientScopeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    // ===== COMPOSITE FK TO CLIENT =====

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

    @Column(name = "scope", nullable = false, length = 128)
    private String scope;

    // ===== EQUALS & HASHCODE =====

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OAuth2ClientScopeEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    // ===== PRE-PERSIST =====

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
