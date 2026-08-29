package com.takibo.authorizationserver.infrastructure.security.tenant;

import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientContextHolder;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import com.takibo.authorizationserver.domain.exception.TenantNotFoundException;
import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP filter that resolves the OAuth2 client from {@code client_id} and stores it in
 * {@link ResolvedOAuthClientContextHolder} (TAS-GRANTS-01).
 * <p>
 * Une seule résolution par requête, partagée par {@link com.takibo.authorizationserver.infrastructure.security.pkce.PkceEnforcementFilter}
 * qui s'exécute après ce filtre dans la chaîne — voir {@code TenantSecurityConfig} et
 * {@code PkceSecurityConfig} pour l'ordre. Aucun tenant n'est fabriqué : un client
 * {@code PLATFORM} publie un {@link ResolvedOAuthClient} sans organisation ni space, et c'est
 * cette absence, pas une valeur par défaut, qui ferme les routes situées en aval.
 * <p>
 * Rejets écrits par {@link OAuth2HttpErrorWriter} — la forme RFC 6749/6750, la même que
 * {@code PkceEnforcementFilter} et que le rejet natif de Spring Authorization Server — jamais
 * par le gestionnaire d'erreurs générique de la plateforme, dont le corps
 * {@code {"code":...,"message":...}} n'est pas celui qu'un client OAuth2 attend sur
 * {@code /oauth2/*}. Un client inconnu répond par le même corps opaque, sans description, que
 * SAS produit déjà pour un secret invalide : la surface ne doit jamais dire laquelle des deux
 * causes s'applique.
 */
@Slf4j
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final ResolvedOAuthClientResolver resolvedOAuthClientResolver;
    private final ClientIdExtractor clientIdExtractor;
    private final OAuth2HttpErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        try {
            String clientId;
            if (isAuthorizeEndpoint(requestUri)) {
                clientId = clientIdExtractor.extractForAuthorize(request);
            } else if (isTokenEndpoint(requestUri)) {
                clientId = clientIdExtractor.extractForToken(request);
            } else if (isDiscoveryEndpoint(requestUri)) {
                clientId = clientIdExtractor.extractForDiscoveryHint(request);
            } else {
                clientId = clientIdExtractor.extractDefault(request);
            }

            if (isDiscoveryEndpoint(requestUri)) {
                if (clientId != null && !clientId.isBlank()) {
                    resolveClient(clientId);
                }
            } else if (isUserInfoEndpoint(requestUri)) {
                clientId = clientIdExtractor.extractForUserInfoHint(request);
                if (clientId != null && !clientId.isBlank()) {
                    resolveClient(clientId);
                }
            } else if (isAuthorizeEndpoint(requestUri)) {
                if (clientId == null || clientId.isBlank()) {
                    errorWriter.writeInvalidRequest("Missing required parameter: client_id", request, response);
                    return;
                }
                resolveClient(clientId);
            } else if (isTokenEndpoint(requestUri)) {
                if (clientId == null || clientId.isBlank()) {
                    errorWriter.writeInvalidClient(null, request, response);
                    return;
                }
                resolveClient(clientId);
            } else if (requiresTenant(requestUri)) {
                if (clientId == null || clientId.isBlank()) {
                    errorWriter.writeInvalidRequest("Client identification required", request, response);
                    return;
                }
                resolveClient(clientId);
            }

            filterChain.doFilter(request, response);

        } catch (TenantNotFoundException e) {
            log.warn("Client not found: {}", e.getMessage());
            // Opaque volontairement, sans description : voir la javadoc de classe.
            errorWriter.writeInvalidClient(null, request, response);

        } finally {
            ResolvedOAuthClientContextHolder.clear();
        }
    }

    private void resolveClient(String clientId) {
        ResolvedOAuthClient client = resolvedOAuthClientResolver.resolve(clientId)
                .orElseThrow(() -> new TenantNotFoundException(clientId));
        ResolvedOAuthClientContextHolder.set(client);
        log.debug("OAuth2 client resolved and set for request");
    }

    private boolean isDiscoveryEndpoint(String uri) {
        return uri.startsWith("/.well-known/");
    }

    private boolean isUserInfoEndpoint(String uri) {
        return uri.startsWith("/userinfo");
    }

    private boolean isAuthorizeEndpoint(String uri) {
        return uri.equals("/oauth2/authorize");
    }

    private boolean isTokenEndpoint(String uri) {
        return uri.equals("/oauth2/token");
    }

    private boolean requiresTenant(String uri) {
        return uri.startsWith("/oauth2/introspect")
            || uri.startsWith("/oauth2/revoke");
    }
}
