package com.takibo.authorizationserver.infrastructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "oauth2_clients")
public class OAuth2ClientLookupEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false, updatable = false)
    private UUID spaceId;

    @Column(name = "client_id", nullable = false, updatable = false, length = 128)
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 20)
    private ClientType clientType;

    @Column(name = "require_pkce", nullable = false)
    private Boolean requirePkce;

    @Column(name = "require_client_secret", nullable = false)
    private Boolean requireClientSecret;

    @Column(name = "client_secret_hash")
    private String clientSecretHash;

    @Column(name = "token_endpoint_auth_method", nullable = false, length = 64)
    private String tokenEndpointAuthMethod;

    @Column(name = "jwks_uri", length = 255)
    private String jwksUri;

    @Column(name = "jwks_json", columnDefinition = "TEXT")
    private String jwksJson;

    @Column(name = "id_token_signed_alg", length = 32)
    private String idTokenSignedAlg;

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getSpaceId() {
        return spaceId;
    }

    public String getClientId() {
        return clientId;
    }

    public ClientType getClientType() {
        return clientType;
    }

    public Boolean getRequirePkce() {
        return requirePkce;
    }

    public Boolean getRequireClientSecret() {
        return requireClientSecret;
    }

    public String getClientSecretHash() {
        return clientSecretHash;
    }

    public String getTokenEndpointAuthMethod() {
        return tokenEndpointAuthMethod;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public String getJwksJson() {
        return jwksJson;
    }

    public String getIdTokenSignedAlg() {
        return idTokenSignedAlg;
    }

    public enum ClientType {
        CONFIDENTIAL,
        PUBLIC
    }
}
