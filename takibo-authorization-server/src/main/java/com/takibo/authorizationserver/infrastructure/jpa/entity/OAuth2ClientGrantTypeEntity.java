package com.takibo.authorizationserver.infrastructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Projection lecture seule de {@code oauth2_client_grant_types}.
 * {@code client_id} référence ici l'identifiant technique du client ({@code oauth2_clients.id}).
 * <p>
 * Nom d'entité explicite pour éviter la collision avec l'entité homonyme du management-service.
 */
@Entity(name = "TasOAuth2ClientGrantTypeLookup")
@Table(name = "oauth2_client_grant_types")
public class OAuth2ClientGrantTypeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "grant_type", nullable = false, length = 64)
    private String grantType;

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getGrantType() {
        return grantType;
    }
}
