package com.takibo.managementservice.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

/**
 * OAuth2 Client Scope Entity
 * Aligned with DDL: oauth2_client_scopes table
 * 
 * DDL (TMS-OAUTH-CLIENT-BOUNDARY-01) : FK simple client_id -> oauth2_clients(id).
 * La frontiere PLATFORM / ORGANIZATION / SPACE appartient au client, jamais a sa
 * configuration : la dupliquer ici permettrait a une ligne de declarer une organisation
 * differente de celle de son client.
 */
@Entity
@Table(
    name = "oauth2_client_scopes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ocs_client_scope_v2",
                         columnNames = {"client_id", "scope"})
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

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    // ===== COMPOSITE FK TO CLIENT =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", referencedColumnName = "id",
                insertable = false, updatable = false)
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
