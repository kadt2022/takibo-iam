package com.takibo.authorizationserver.infrastructure.security.pkce;

import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientContextHolder;
import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import com.takibo.authorizationserver.infrastructure.security.tenant.ClientIdExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Applique la politique PKCE du client résolu par {@code client_id} (TAS-GRANTS-01).
 * <p>
 * Lit {@link ResolvedOAuthClientContextHolder}, peuplé par {@code TenantResolutionFilter} qui
 * s'exécute avant ce filtre dans la chaîne — voir {@code TenantSecurityConfig} et
 * {@code PkceSecurityConfig} pour l'ordre. Plus aucun second lookup par
 * {@code (org_id, space_id, client_id)} : {@link ResolvedOAuthClient#pkceRequired()} porte
 * déjà la règle complète, y compris pour un client {@code PLATFORM} sans organisation ni
 * space — la politique ne dépend jamais de ce que la requête prétend connaître du tenant.
 */
public class PkceEnforcementFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_ENDPOINT = "/oauth2/authorize";
    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
    private static final String PKCE_METHOD_S256 = "S256";

    private final ClientIdExtractor clientIdExtractor;
    private final OAuth2HttpErrorWriter errorWriter;

    public PkceEnforcementFilter(ClientIdExtractor clientIdExtractor, OAuth2HttpErrorWriter errorWriter) {
        this.clientIdExtractor = clientIdExtractor;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        boolean isAuthorize = AUTHORIZATION_ENDPOINT.equals(uri);
        boolean isToken = TOKEN_ENDPOINT.equals(uri);
        if (!isAuthorize && !isToken) {
            chain.doFilter(request, response);
            return;
        }

        // PKCE only applies to authorization_code; skip all client lookup for other grants on /oauth2/token
        if (isToken && !GRANT_TYPE_AUTHORIZATION_CODE.equals(request.getParameter("grant_type"))) {
            chain.doFilter(request, response);
            return;
        }

        String clientId = isAuthorize
                ? clientIdExtractor.extractForAuthorize(request)
                : clientIdExtractor.extractForToken(request);
        if (!StringUtils.hasText(clientId)) {
            if (isAuthorize) {
                errorWriter.writeInvalidRequest("Missing required parameter: client_id", request, response);
            } else {
                errorWriter.writeInvalidClient("Client identification required", request, response);
            }
            return;
        }

        ResolvedOAuthClient client = ResolvedOAuthClientContextHolder.get();
        if (client == null) {
            errorWriter.writeInvalidRequest("Tenant context not set", request, response);
            return;
        }

        boolean pkceRequired = client.pkceRequired();

        if (isAuthorize) {
            if (!enforceAuthorize(request, response, pkceRequired)) {
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        if (!enforceToken(request, response, pkceRequired)) {
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean enforceAuthorize(HttpServletRequest request, HttpServletResponse response, boolean pkceRequired)
            throws IOException {
        String codeChallenge = request.getParameter("code_challenge");
        String codeChallengeMethod = request.getParameter("code_challenge_method");

        if (StringUtils.hasText(codeChallenge)) {
            if (!PKCE_METHOD_S256.equals(codeChallengeMethod)) {
                errorWriter.writeInvalidRequest("code_challenge_method must be S256", request, response);
                return false;
            }
            return true;
        }

        if (StringUtils.hasText(codeChallengeMethod)) {
            errorWriter.writeInvalidRequest("code_challenge required", request, response);
            return false;
        }

        if (pkceRequired) {
            errorWriter.writeInvalidRequest("code_challenge required", request, response);
            return false;
        }
        return true;
    }

    private boolean enforceToken(HttpServletRequest request, HttpServletResponse response, boolean pkceRequired)
            throws IOException {
        String grantType = request.getParameter("grant_type");
        if (!GRANT_TYPE_AUTHORIZATION_CODE.equals(grantType)) {
            return true;
        }

        String codeVerifier = request.getParameter("code_verifier");
        if (pkceRequired && !StringUtils.hasText(codeVerifier)) {
            errorWriter.writeInvalidRequest("code_verifier required", request, response);
            return false;
        }
        return true;
    }
}
