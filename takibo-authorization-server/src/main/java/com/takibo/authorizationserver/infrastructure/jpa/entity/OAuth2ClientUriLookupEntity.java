package com.takibo.authorizationserver.infrastructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.util.UUID;

/**
 * Structure commune des tables d'URIs d'un client OAuth2 ({@code redirect_uris},
 * {@code post_logout_redirect_uris}) : {@code id}, {@code client_id} (= {@code oauth2_clients.id})
 * et une {@code uri}. Lecture seule. Les sous-classes ne fournissent que le mapping de table.
 */
@MappedSuperclass
public abstract class OAuth2ClientUriLookupEntity {

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
