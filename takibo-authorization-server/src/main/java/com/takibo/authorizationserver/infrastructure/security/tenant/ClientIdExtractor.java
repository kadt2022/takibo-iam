package com.takibo.authorizationserver.infrastructure.security.tenant;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Extracts OAuth2 client_id from HTTP request.
 *
 * Supports multiple methods (in priority order):
 * 1. Query parameter ?client_id=...
 * 2. Form parameter client_id=...
 * 3. Authorization: Basic header (client_id:client_secret encoded)
 */
@Slf4j
public class ClientIdExtractor {

    public String extractForAuthorize(HttpServletRequest request) {
        return extractFromParameter(request);
    }

    public String extractForDiscoveryHint(HttpServletRequest request) {
        return extractFromParameter(request);
    }

    public String extractForToken(HttpServletRequest request) {
        String clientId = extractFromBasicAuthHeader(request);
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }
        return extractFromParameter(request);
    }

    public String extractForUserInfoHint(HttpServletRequest request) {
        return extractFromParameter(request);
    }

    public String extractDefault(HttpServletRequest request) {
        String clientId = extractFromParameter(request);
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }
        return extractFromBasicAuthHeader(request);
    }

    private String extractFromParameter(HttpServletRequest request) {
        String clientId = request.getParameter("client_id");
        if (clientId != null && !clientId.isBlank()) {
            log.debug("Extracted client_id from query/form parameter");
            return clientId;
        }
        return null;
    }

    private String extractFromBasicAuthHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return null;
        }

        try {
            String base64Credentials = authHeader.substring(6);
            byte[] decoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(decoded, StandardCharsets.UTF_8);

            int colonIndex = credentials.indexOf(':');
            if (colonIndex <= 0) {
                return null;
            }

            String clientId = credentials.substring(0, colonIndex);
            if (clientId.isBlank()) {
                return null;
            }

            log.debug("Extracted client_id from Basic Auth header");
            return clientId;

        } catch (Exception e) {
            log.warn("Failed to extract client_id from Basic Auth: {}", e.getMessage());
            return null;
        }
    }
}
