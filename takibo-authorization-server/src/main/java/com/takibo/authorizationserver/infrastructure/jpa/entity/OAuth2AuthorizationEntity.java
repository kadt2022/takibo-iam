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
import java.util.UUID;

/**
 * Ligne {@code oauth2_authorization} (TAS-GRANTS-02) : autorisation, codes, tokens et
 * consentement implicite d'une émission OAuth2/OIDC.
 * <p>
 * {@code org_id}/{@code space_id} suivent {@code ResolvedOAuthClient.plan()} : tous deux NULL
 * pour PLATFORM, {@code org_id} seul pour ORGANIZATION, les deux pour SPACE — voir
 * V202608290001. {@code subjectType}/{@code principalName} distinguent CLIENT_APP
 * ({@code client_credentials} : le principal est le client, {@code principalAccountId} reste
 * NULL) de HUMAN ({@code principalAccountId} peut rester NULL le temps qu'un device code soit
 * approuvé).
 * <p>
 * Les colonnes {@code *_value} portent un chiffre autoportant (voir
 * {@link com.takibo.authorizationserver.domain.authorization.EncryptedTokenValue}), jamais un
 * clair ; les colonnes {@code *_hash} sont la clé de recherche
 * ({@link com.takibo.authorizationserver.domain.authorization.TokenHash}). {@code attributes}
 * et chaque {@code *_metadata} portent du JSON déjà sérialisé par l'{@code ObjectMapper}
 * configuré avec les modules Jackson de Spring Authorization Server — cette entité ne fait
 * aucune conversion, elle relaie le texte tel quel vers/depuis la colonne {@code jsonb}.
 */
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

    /** NULL = PLATFORM. Jamais renseigné sans que le client résolu ne le porte lui-même. */
    @Column(name = "org_id")
    private UUID orgId;

    /** NULL pour PLATFORM et ORGANIZATION ; requis pour SPACE. */
    @Column(name = "space_id")
    private UUID spaceId;

    /** {@code RegisteredClient.getId()}, jamais le {@code client_id} public. */
    @Column(name = "registered_client_id", nullable = false, length = 128)
    private String registeredClientId;

    /** NULL pour CLIENT_APP, et pour un sujet HUMAN pas encore approuvé (device code). */
    @Column(name = "principal_account_id")
    private UUID principalAccountId;

    /** {@code CLIENT_APP} ou {@code HUMAN} — voir la javadoc de classe. */
    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    /** {@code OAuth2Authorization.getPrincipalName()} tel quel. */
    @Column(name = "principal_name", nullable = false, length = 255)
    private String principalName;

    @Column(name = "authorization_grant_type", nullable = false, length = 100)
    private String authorizationGrantType;

    @Column(name = "authorized_scopes", length = 2000)
    private String authorizedScopes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private String attributes;

    @Column(name = "state", length = 500)
    private String state;

    // ========== AUTHORIZATION CODE (VALUE CHIFFRÉE + HASH) ==========

    @Column(name = "authorization_code_value", columnDefinition = "text")
    private String authorizationCodeValue;

    @Column(name = "authorization_code_hash", columnDefinition = "char(64)")
    private String authorizationCodeHash;

    @Column(name = "authorization_code_issued_at")
    private OffsetDateTime authorizationCodeIssuedAt;

    @Column(name = "authorization_code_expires_at")
    private OffsetDateTime authorizationCodeExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "authorization_code_metadata", columnDefinition = "jsonb")
    private String authorizationCodeMetadata;

    // ========== ACCESS TOKEN (VALUE + HASH) ==========

    @Column(name = "access_token_value", columnDefinition = "text")
    private String accessTokenValue;

    @Column(name = "access_token_hash", columnDefinition = "char(64)")
    private String accessTokenHash;

    @Column(name = "access_token_issued_at")
    private OffsetDateTime accessTokenIssuedAt;

    @Column(name = "access_token_expires_at")
    private OffsetDateTime accessTokenExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "access_token_metadata", columnDefinition = "jsonb")
    private String accessTokenMetadata;

    @Column(name = "access_token_type", length = 100)
    private String accessTokenType;

    @Column(name = "access_token_scopes", length = 2000)
    private String accessTokenScopes;

    // ========== OIDC ID TOKEN (VALUE + HASH) ==========

    @Column(name = "oidc_id_token_value", columnDefinition = "text")
    private String oidcIdTokenValue;

    @Column(name = "oidc_id_token_hash", columnDefinition = "char(64)")
    private String oidcIdTokenHash;

    @Column(name = "oidc_id_token_issued_at")
    private OffsetDateTime oidcIdTokenIssuedAt;

    @Column(name = "oidc_id_token_expires_at")
    private OffsetDateTime oidcIdTokenExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "oidc_id_token_metadata", columnDefinition = "jsonb")
    private String oidcIdTokenMetadata;

    // ========== REFRESH TOKEN (VALUE + HASH) ==========

    @Column(name = "refresh_token_value", columnDefinition = "text")
    private String refreshTokenValue;

    @Column(name = "refresh_token_hash", columnDefinition = "char(64)")
    private String refreshTokenHash;

    @Column(name = "refresh_token_issued_at")
    private OffsetDateTime refreshTokenIssuedAt;

    @Column(name = "refresh_token_expires_at")
    private OffsetDateTime refreshTokenExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "refresh_token_metadata", columnDefinition = "jsonb")
    private String refreshTokenMetadata;

    // ========== USER CODE (VALUE CHIFFRÉE + HASH) ==========

    @Column(name = "user_code_value", columnDefinition = "text")
    private String userCodeValue;

    @Column(name = "user_code_hash", columnDefinition = "char(64)")
    private String userCodeHash;

    @Column(name = "user_code_issued_at")
    private OffsetDateTime userCodeIssuedAt;

    @Column(name = "user_code_expires_at")
    private OffsetDateTime userCodeExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_code_metadata", columnDefinition = "jsonb")
    private String userCodeMetadata;

    // ========== DEVICE CODE (VALUE CHIFFRÉE + HASH) ==========

    @Column(name = "device_code_value", columnDefinition = "text")
    private String deviceCodeValue;

    @Column(name = "device_code_hash", columnDefinition = "char(64)")
    private String deviceCodeHash;

    @Column(name = "device_code_issued_at")
    private OffsetDateTime deviceCodeIssuedAt;

    @Column(name = "device_code_expires_at")
    private OffsetDateTime deviceCodeExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "device_code_metadata", columnDefinition = "jsonb")
    private String deviceCodeMetadata;

    // ========== TIMESTAMPS ==========

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
