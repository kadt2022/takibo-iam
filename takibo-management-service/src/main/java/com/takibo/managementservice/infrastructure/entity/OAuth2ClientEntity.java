package com.takibo.managementservice.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.*;

/**
 * OAuth2 Client Entity - OAuth2/OIDC Client Application
 * Aligned with DDL: oauth2_clients table
 * 
 * DDL Constraints:
 * - CONSTRAINT uk_oauth2_clients_scope_id UNIQUE (org_id, space_id, id)
 * - CONSTRAINT uk_oauth2_clients_scope_client_id UNIQUE (org_id, space_id, client_id)
 * - Composite FK (org_id, space_id) -> spaces(org_id, id)
 */
@Entity
@Table(
    name = "oauth2_clients",
    indexes = {
        @Index(name = "idx_oauth2_clients_org_space", columnList = "org_id, space_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_oauth2_clients_scope_id", 
                         columnNames = {"org_id", "space_id", "id"}),
        @UniqueConstraint(name = "uq_oauth2_clients_scope_client_id", 
                         columnNames = {"org_id", "space_id", "client_id"})
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuth2ClientEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    // ===== COMPOSITE FK TO SPACE =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "org_id", referencedColumnName = "org_id", 
                    insertable = false, updatable = false),
        @JoinColumn(name = "space_id", referencedColumnName = "id", 
                    insertable = false, updatable = false)
    })
    private SpaceEntity space;

    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    @CreatedDate
    @Column(name = "client_id_issued_at", nullable = false, updatable = false)
    private Instant clientIdIssuedAt;

    @Column(name = "client_name", nullable = false, length = 160)
    private String clientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 20)
    @Builder.Default
    private ClientType clientType = ClientType.CONFIDENTIAL;

    @Column(name = "require_client_secret", nullable = false)
    @Builder.Default
    private Boolean requireClientSecret = true;

    @Column(name = "client_secret_hash", length = 255)
    private String clientSecretHash;

    @Column(name = "client_secret_expires_at")
    private Instant clientSecretExpiresAt;

    @Column(name = "token_endpoint_auth_method", nullable = false, length = 64)
    @Builder.Default
    private String tokenEndpointAuthMethod = "client_secret_basic";

    @Column(name = "require_pkce", nullable = false)
    @Builder.Default
    private Boolean requirePkce = false;

    @Column(name = "require_consent", nullable = false)
    @Builder.Default
    private Boolean requireConsent = false;

    @Column(name = "jwks_uri", length = 255)
    private String jwksUri;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "jwks_json", columnDefinition = "TEXT")
    private String jwksJson;

    @Column(name = "id_token_signed_alg", length = 32)
    private String idTokenSignedAlg;

    @Column(name = "access_token_ttl_seconds")
    private Integer accessTokenTtlSeconds;

    @Column(name = "refresh_token_ttl_seconds")
    private Integer refreshTokenTtlSeconds;

    @Column(name = "id_token_ttl_seconds")
    private Integer idTokenTtlSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_settings", columnDefinition = "jsonb")
    private Map<String, Object> additionalSettings;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // ===== ONE-TO-MANY RELATIONS =====

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OAuth2ClientScopeEntity> scopes = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OAuth2ClientGrantTypeEntity> grantTypes = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OAuth2ClientRedirectUriEntity> redirectUris = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OAuth2ClientPostLogoutRedirectUriEntity> postLogoutRedirectUris = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OAuth2ClientCorsOriginEntity> corsOrigins = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OAuth2ClientSecretHistoryEntity> secretHistory = new ArrayList<>();

    // ===== ENUM =====

    public enum ClientType {
        CONFIDENTIAL,
        PUBLIC
    }

    // ===== EQUALS & HASHCODE =====

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OAuth2ClientEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    // ===== PRE-PERSIST =====

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (id == null) {
            id = UUID.randomUUID();
        }
        if (clientType == null) {
            clientType = ClientType.CONFIDENTIAL;
        }
        if (requireClientSecret == null) {
            requireClientSecret = true;
        }
        if (requirePkce == null) {
            requirePkce = false;
        }
        if (requireConsent == null) {
            requireConsent = false;
        }
        if (tokenEndpointAuthMethod == null) {
            tokenEndpointAuthMethod = "client_secret_basic";
        }
        if (clientIdIssuedAt == null) {
            clientIdIssuedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}
