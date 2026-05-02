package com.takibo.securitymanagement.sentinel.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Entry point appelé quand l'utilisateur n'est pas authentifié
 * (pas de JWT ou contexte vide) sur une ressource protégée.
 *
 * → Utilise Sentinel + RuleRegistry (AuthenticationException)
 */
@Component
@RequiredArgsConstructor
public class SentinelAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SentinelHttpErrorWriter sentinelHttpErrorWriter;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // 401 AUTHENTICATION_FAILED (voir DefaultRules)
        sentinelHttpErrorWriter.write(authException, request, response);
    }
}
