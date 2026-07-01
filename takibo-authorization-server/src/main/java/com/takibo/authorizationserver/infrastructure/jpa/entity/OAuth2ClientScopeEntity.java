package com.takibo.authorizationserver.infrastructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Projection lecture seule de {@code oauth2_client_scopes}.
 * {@code client_id} référence ici l'identifiant technique du client ({@code oauth2_clients.id}).
 * <p>
 * Nom d'entité explicite pour ne pas entrer en collision avec l'entité homonyme du
 * management-service (même table, même nom de classe simple).
 */
@Entity(name = "TasOAuth2ClientScopeLookup")
@Table(name = "oauth2_client_scopes")
public class OAuth2ClientScopeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "scope", nullable = false, length = 128)
    private String scope;

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getScope() {
        return scope;
    }
}
