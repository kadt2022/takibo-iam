package com.takibo.authorizationserver.infrastructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Projection lecture seule de {@code oauth2_client_post_logout_redirect_uris}.
 * {@code client_id} référence l'identifiant technique du client ({@code oauth2_clients.id}).
 * Nom d'entité explicite pour éviter la collision avec l'entité homonyme du management-service.
 */
@Entity(name = "TasOAuth2ClientPostLogoutRedirectUriLookup")
@Table(name = "oauth2_client_post_logout_redirect_uris")
public class OAuth2ClientPostLogoutRedirectUriEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "uri", nullable = false, length = 255)
    private String uri;

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getUri() {
        return uri;
    }
}
