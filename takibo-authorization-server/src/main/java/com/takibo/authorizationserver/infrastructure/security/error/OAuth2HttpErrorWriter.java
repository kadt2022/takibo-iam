package com.takibo.authorizationserver.infrastructure.security.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes OAuth2/OIDC standard error responses (RFC 6749, RFC 6750).
 *
 * Produces JSON responses in the format:
 * {
 *   "error": "invalid_client",
 *   "error_description": "Client authentication required",
 *   "error_uri": "https://takibo.com/docs/errors/invalid_client" (optional)
 * }
 *
 * Also sets WWW-Authenticate header for 401 responses per RFC 6750.
 */
public class OAuth2HttpErrorWriter {

    private final ObjectMapper objectMapper;

    public OAuth2HttpErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeInvalidRequest(String description, HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(response, request, 400, "invalid_request", description, null, null);
    }

    public void writeInvalidClient(String description, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String wwwAuthenticate = buildWwwAuthenticate("Basic", "invalid_client", description);
        write(response, request, 401, "invalid_client", description, wwwAuthenticate, null);
    }

    public void writeInvalidToken(String description, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String wwwAuthenticate = buildWwwAuthenticate("Bearer", "invalid_token", description);
        write(response, request, 401, "invalid_token", description, wwwAuthenticate, null);
    }

    public void writeServerError(String description, HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(response, request, 500, "server_error", description, null, null);
    }

    private void write(HttpServletResponse response,
                       HttpServletRequest request,
                       int status,
                       String error,
                       String errorDescription,
                       String wwwAuthenticate,
                       String errorUri) throws IOException {

        String traceId = Optional.ofNullable(MDC.get("traceId"))
                .filter(s -> !s.isBlank())
                .orElse(UUID.randomUUID().toString());

        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Trace-Id", traceId);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");

        if (wwwAuthenticate != null && !wwwAuthenticate.isBlank()) {
            response.setHeader("WWW-Authenticate", wwwAuthenticate);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        if (errorDescription != null && !errorDescription.isBlank()) {
            body.put("error_description", errorDescription);
        }
        if (errorUri != null && !errorUri.isBlank()) {
            body.put("error_uri", errorUri);
        }

        objectMapper.writeValue(response.getWriter(), body);
    }

    private String buildWwwAuthenticate(String scheme, String error, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append(" realm=\"takibo\"");
        if (error != null && !error.isBlank()) {
            sb.append(", error=\"").append(escape(error)).append("\"");
        }
        if (description != null && !description.isBlank()) {
            sb.append(", error_description=\"").append(escape(description)).append("\"");
        }
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\"", "'");
    }
}
