package com.takibo.authorizationserver.infrastructure.security.tenant;

import com.takibo.authorizationserver.domain.exception.TakiboInvalidClientException;
import com.takibo.authorizationserver.domain.exception.TakiboInvalidRequestException;
import com.takibo.authorizationserver.domain.exception.TakiboServerErrorException;
import com.takibo.authorizationserver.domain.exception.TenantNotFoundException;
import com.takibo.authorizationserver.domain.security.tenant.TenantContext;
import com.takibo.authorizationserver.domain.security.tenant.TenantContextHolder;
import com.takibo.authorizationserver.domain.security.tenant.TenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * HTTP filter that resolves tenant context from client_id and stores in TenantContextHolder.
 */
@Slf4j
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;
    private final ClientIdExtractor clientIdExtractor;
    private final HandlerExceptionResolver handlerExceptionResolver;

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
                    resolveTenant(clientId);
                }
            } else if (isUserInfoEndpoint(requestUri)) {
                clientId = clientIdExtractor.extractForUserInfoHint(request);
                if (clientId != null && !clientId.isBlank()) {
                    resolveTenant(clientId);
                }
            } else if (isAuthorizeEndpoint(requestUri)) {
                if (clientId == null || clientId.isBlank()) {
                    writeOAuth2OrThrowInvalidRequest("Missing required parameter: client_id", request, response);
                    return;
                }
                resolveTenant(clientId);
            } else if (isTokenEndpoint(requestUri)) {
                if (clientId == null || clientId.isBlank()) {
                    writeOAuth2OrThrowInvalidClient("Client authentication required", request, response);
                    return;
                }
                resolveTenant(clientId);
            } else if (requiresTenant(requestUri)) {
                if (clientId == null || clientId.isBlank()) {
                    writeOAuth2OrThrowInvalidRequest("Client identification required", request, response);
                    return;
                }
                resolveTenant(clientId);
            }

            filterChain.doFilter(request, response);

        } catch (TenantNotFoundException e) {
            log.warn("Tenant not found: {}", e.getMessage());
            writeOAuth2OrThrowInvalidClient("Client not found: " + e.getMessage(), request, response);

        } catch (TakiboServerErrorException.TenantResolutionException e) {
            log.error("Tenant resolution failed (system error)", e);
            writeOAuth2OrThrowServerError("Failed to resolve tenant", request, response);

        } finally {
            TenantContextHolder.clear();
        }
    }

    private void resolveTenant(String clientId) {
        TenantContext context = tenantResolver.resolve(clientId);
        TenantContextHolder.set(context);
        log.debug("Tenant context resolved and set for request");
    }

    private void writeOAuth2OrThrowInvalidRequest(String message, HttpServletRequest request, HttpServletResponse response) {
        if (isOAuth2Surface(request.getRequestURI())) {
            resolveWithSentinel(request, response, new TakiboInvalidRequestException(message));
        } else {
            throw new IllegalArgumentException(message);
        }
    }

    private void writeOAuth2OrThrowInvalidClient(String message, HttpServletRequest request, HttpServletResponse response) {
        if (isOAuth2Surface(request.getRequestURI())) {
            resolveWithSentinel(request, response, new TakiboInvalidClientException(message));
        } else {
            throw new IllegalArgumentException(message);
        }
    }

    private void writeOAuth2OrThrowServerError(String message, HttpServletRequest request, HttpServletResponse response) {
        if (isOAuth2Surface(request.getRequestURI())) {
            resolveWithSentinel(request, response, new TakiboServerErrorException(message, "TENANT_RESOLUTION_FAILED"));
        } else {
            throw new IllegalStateException(message);
        }
    }

    private void resolveWithSentinel(HttpServletRequest request, HttpServletResponse response, RuntimeException ex) {
        if (handlerExceptionResolver.resolveException(request, response, null, ex) == null && !response.isCommitted()) {
            throw ex;
        }
    }

    private boolean isOAuth2Surface(String uri) {
        return uri.startsWith("/oauth2/")
                || uri.startsWith("/.well-known/")
                || uri.startsWith("/userinfo");
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
