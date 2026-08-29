package com.takibo.authorizationserver.domain.client;

/**
 * Nature du client au sens OAuth2 : capable ou non de garder un secret.
 * <p>
 * Un client {@link #PUBLIC} ne peut prouver son identite par un secret ; PKCE lui est donc
 * exige quoi qu'en dise sa configuration. Voir {@link ResolvedOAuthClient#pkceRequired()}.
 */
public enum ClientType {
    CONFIDENTIAL,
    PUBLIC
}
