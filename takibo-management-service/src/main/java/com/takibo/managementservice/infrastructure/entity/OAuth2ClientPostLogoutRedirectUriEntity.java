package com.takibo.managementservice.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

/**
 * OAuth2 Client Post-Logout Redirect URI Entity
 * Aligned with DDL: oauth2_client_post_logout_redirect_uris table
 */
@Entity
@Table(
    name = "oauth2_client_post_logout_redirect_uris",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ocplr_client_post_logout_v2",
                         columnNames = {"client_id", "uri"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuth2ClientPostLogoutRedirectUriEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", referencedColumnName = "id",
                insertable = false, updatable = false)
    private OAuth2ClientEntity client;

    @Column(name = "uri", nullable = false, length = 255)
    private String uri;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OAuth2ClientPostLogoutRedirectUriEntity that)) return false;
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
