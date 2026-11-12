package com.takibo.authorizationserver.infrastructure.security.authorize;

import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientContextHolder;
import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SEC-TAS-04 — un client qui ne peut pas recevoir de code d'autorisation est refusé sur place
 * par {@code /oauth2/authorize}, avant Spring Authorization Server.
 * <p>
 * Spring Authorization Server 7.0.3 valide la requête dans un filtre interne
 * ({@code OAuth2AuthorizationCodeRequestValidatingFilter}) qui contrôle le grant
 * {@code authorization_code} <b>avant</b> le {@code redirect_uri}. Pour un client machine, il
 * construisait l'erreur {@code unauthorized_client} vers l'URI brute de la requête (redirection
 * ouverte), ou, sans {@code redirect_uri}, échouait sur le premier élément d'un ensemble d'URI
 * enregistrées vide ({@code NoSuchElementException}, 500).
 * <p>
 * Ce filtre lit le client déjà résolu par {@code TenantResolutionFilter} et pose la seule
 * question qui ne dépend d'aucune URI : ce client peut-il demander un code ? Sinon :
 * {@code 400 unauthorized_client}, sans destination. Les clients autorisés passent, et Spring
 * Authorization Server les valide sans changement (URI, scope, PKCE, restriction OIDC).
 */
@Slf4j
public class AuthorizationCodeClientFilter extends OncePerRequestFilter {

    static final String AUTHORIZATION_ENDPOINT = "/oauth2/authorize";
    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1";

    private final OAuth2HttpErrorWriter errorWriter;

    public AuthorizationCodeClientFilter(OAuth2HttpErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AUTHORIZATION_ENDPOINT.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ResolvedOAuthClient client = ResolvedOAuthClientContextHolder.get();
        if (client == null) {
            // TenantResolutionFilter refuse déjà un client absent ou inconnu : un contexte vide
            // ici est une anomalie de câblage, refusée plutôt que confiée à la chaîne aval.
            errorWriter.writeInvalidRequest("Tenant context not set", request, response);
            return;
        }
        if (!client.supportsGrantType(AuthorizationGrantType.AUTHORIZATION_CODE.getValue())
                || client.redirectUris().isEmpty()) {
            log.warn("Authorization request refused: client {} cannot request an authorization code",
                    client.clientId());
            errorWriter.writeAuthorizationError(new OAuth2Error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT,
                    "The client is not authorized to request an authorization code", ERROR_URI), request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
