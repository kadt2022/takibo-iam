package com.takibo.authorizationserver.infrastructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "oauth2_authorization",
        indexes = {
                @Index(name = "idx_oauth2_authz_org_space", columnList = "org_id, space_id"),
                @Index(name = "idx_oauth2_authz_client", columnList = "org_id, space_id, registered_client_id"),
                @Index(name = "idx_oauth2_authz_account", columnList = "org_id, principal_account_id"),
                @Index(name = "idx_oauth2_authz_code_hash", columnList = "authorization_code_hash"),
                @Index(name = "idx_oauth2_authz_access_hash", columnList = "access_token_hash"),
                @Index(name = "idx_oauth2_authz_id_hash", columnList = "oidc_id_token_hash"),
                @Index(name = "idx_oauth2_authz_refresh_hash", columnList = "refresh_token_hash"),
                @Index(name = "idx_oauth2_authz_user_code_hash", columnList = "user_code_hash"),
                @Index(name = "idx_oauth2_authz_device_code_hash", columnList = "device_code_hash")
        }
)
public class OAuth2AuthorizationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "registered_client_id", nullable = false, length = 128)
    private String registeredClientId;

    @Column(name = "principal_account_id", nullable = false)
    private UUID principalAccountId;

    @Column(name = "authorization_grant_type", nullable = false, length = 100)
    private String authorizationGrantType;

    @Column(name = "authorized_scopes", length = 2000)
    private String authorizedScopes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    @Column(name = "state", length = 500)
    private String state;

    // ========== AUTHORIZATION CODE (HASH-ONLY) ==========

    @Column(name = "authorization_code_hash", columnDefinition = "char(64)")
    private String authorizationCodeHash;

    @Column(name = "authorization_code_issued_at")
    private OffsetDateTime authorizationCodeIssuedAt;

    @Column(name = "authorization_code_expires_at")
    private OffsetDateTime authorizationCodeExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "authorization_code_metadata", columnDefinition = "jsonb")
    private Map<String, Object> authorizationCodeMetadata;

    // ========== ACCESS TOKEN (VALUE + HASH) ==========

    @Column(name = "access_token_value", length = 16000)
    private String accessTokenValue;

    @Column(name = "access_token_hash", columnDefinition = "char(64)")
    private String accessTokenHash;

    @Column(name = "access_token_issued_at")
    private OffsetDateTime accessTokenIssuedAt;

    @Column(name = "access_token_expires_at")
    private OffsetDateTime accessTokenExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "access_token_metadata", columnDefinition = "jsonb")
    private Map<String, Object> accessTokenMetadata;

    @Column(name = "access_token_type", length = 100)
    private String accessTokenType;

    @Column(name = "access_token_scopes", length = 2000)
    private String accessTokenScopes;

    // ========== OIDC ID TOKEN (VALUE + HASH) ==========

    @Column(name = "oidc_id_token_value", length = 16000)
    private String oidcIdTokenValue;

    @Column(name = "oidc_id_token_hash", columnDefinition = "char(64)")
    private String oidcIdTokenHash;

    @Column(name = "oidc_id_token_issued_at")
    private OffsetDateTime oidcIdTokenIssuedAt;

    @Column(name = "oidc_id_token_expires_at")
    private OffsetDateTime oidcIdTokenExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "oidc_id_token_metadata", columnDefinition = "jsonb")
    private Map<String, Object> oidcIdTokenMetadata;

    // ========== REFRESH TOKEN (VALUE + HASH) ==========

    @Column(name = "refresh_token_value", length = 4000)
    private String refreshTokenValue;

    @Column(name = "refresh_token_hash", columnDefinition = "char(64)")
    private String refreshTokenHash;

    @Column(name = "refresh_token_issued_at")
    private OffsetDateTime refreshTokenIssuedAt;

    @Column(name = "refresh_token_expires_at")
    private OffsetDateTime refreshTokenExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "refresh_token_metadata", columnDefinition = "jsonb")
    private Map<String, Object> refreshTokenMetadata;

    // ========== USER CODE (HASH-ONLY) ==========

    @Column(name = "user_code_hash", columnDefinition = "char(64)")
    private String userCodeHash;

    @Column(name = "user_code_issued_at")
    private OffsetDateTime userCodeIssuedAt;

    @Column(name = "user_code_expires_at")
    private OffsetDateTime userCodeExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_code_metadata", columnDefinition = "jsonb")
    private Map<String, Object> userCodeMetadata;

    // ========== DEVICE CODE (HASH-ONLY) ==========

    @Column(name = "device_code_hash", columnDefinition = "char(64)")
    private String deviceCodeHash;

    @Column(name = "device_code_issued_at")
    private OffsetDateTime deviceCodeIssuedAt;

    @Column(name = "device_code_expires_at")
    private OffsetDateTime deviceCodeExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "device_code_metadata", columnDefinition = "jsonb")
    private Map<String, Object> deviceCodeMetadata;

    // ========== TIMESTAMPS ==========

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
