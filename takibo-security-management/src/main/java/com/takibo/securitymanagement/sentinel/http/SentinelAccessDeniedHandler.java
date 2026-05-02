package com.takibo.securitymanagement.sentinel.http;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * AccessDeniedHandler qui renvoie une réponse Sentinel
 * quand l'utilisateur est authentifié mais n'a pas les droits (403).
 */
@Component
@RequiredArgsConstructor
public class SentinelAccessDeniedHandler implements AccessDeniedHandler {

    private final SentinelHttpErrorWriter sentinelHttpErrorWriter;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        // → 403 ACCESS_DENIED (via DefaultRules)
        sentinelHttpErrorWriter.write(accessDeniedException, request, response);
    }
}
