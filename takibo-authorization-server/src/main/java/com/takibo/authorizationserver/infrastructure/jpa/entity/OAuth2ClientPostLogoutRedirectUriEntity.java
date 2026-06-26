package com.takibo.authorizationserver.infrastructure.jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Projection lecture seule de {@code oauth2_client_post_logout_redirect_uris}.
 * Nom d'entité explicite pour éviter la collision avec l'entité homonyme du management-service.
 */
@Entity(name = "TasOAuth2ClientPostLogoutRedirectUriLookup")
@Table(name = "oauth2_client_post_logout_redirect_uris")
public class OAuth2ClientPostLogoutRedirectUriEntity extends OAuth2ClientUriLookupEntity {
}
