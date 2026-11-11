package com.takibo.authorizationserver.infrastructure.springauthserver.authorization;

import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

/**
 * SEC-TAS-04 — réponse d'erreur de {@code /oauth2/authorize} : jamais vers une URI non enregistrée.
 * <p>
 * Invariant : une erreur n'est renvoyée par redirection (RFC 6749 §4.1.2.1) que vers une URI
 * enregistrée pour ce client. Dans tous les autres cas — URI absente, inconnue, client
 * introuvable — l'erreur est écrite sur place, en {@code 400} au format OAuth2. La garantie ne
 * dépend plus de l'ordre interne des contrôles de Spring Authorization Server : elle tient au
 * seul point de sortie des erreurs de ce point de terminaison.
 * <p>
 * Correspondance : égalité exacte ; pour une adresse de bouclage IP ({@code 127.0.0.1},
 * {@code [::1]}), le port est ignoré, comme le fait Spring Authorization Server (RFC 8252 §7.3).
 */
@Slf4j
public final class RegisteredRedirectUriErrorHandler implements AuthenticationFailureHandler {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "[::1]", "[0:0:0:0:0:0:0:1]");

    private final RegisteredClientRepository registeredClients;
    private final OAuth2HttpErrorWriter errorWriter;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public RegisteredRedirectUriErrorHandler(RegisteredClientRepository registeredClients,
                                             OAuth2HttpErrorWriter errorWriter) {
        this.registeredClients = registeredClients;
        this.errorWriter = errorWriter;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        OAuth2Error error = exception instanceof OAuth2AuthenticationException oauth2
                ? oauth2.getError()
                : new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST);
        OAuth2AuthorizationCodeRequestAuthenticationToken authorizationRequest =
                exception instanceof OAuth2AuthorizationCodeRequestAuthenticationException codeRequest
                        ? codeRequest.getAuthorizationCodeRequestAuthentication()
                        : null;
        String redirectUri = authorizationRequest != null ? authorizationRequest.getRedirectUri() : null;
        if (StringUtils.hasText(redirectUri) && isRegistered(authorizationRequest.getClientId(), redirectUri)) {
            redirectStrategy.sendRedirect(request, response,
                    errorRedirect(redirectUri, error, authorizationRequest.getState()));
            return;
        }
        if (StringUtils.hasText(redirectUri)) {
            log.warn("Authorization error not redirected: redirect_uri is not registered for client {} error={}",
                    authorizationRequest.getClientId(), error.getErrorCode());
        }
        errorWriter.writeAuthorizationError(error, request, response);
    }

    private boolean isRegistered(String clientId, String redirectUri) {
        RegisteredClient client = StringUtils.hasText(clientId) ? registeredClients.findByClientId(clientId) : null;
        return client != null && matchesOneOf(client.getRedirectUris(), redirectUri);
    }

    static boolean matchesOneOf(Set<String> registered, String requested) {
        if (registered.contains(requested)) {
            return true;
        }
        UriComponents candidate = parse(requested);
        if (candidate == null || !LOOPBACK_HOSTS.contains(candidate.getHost())) {
            return false;
        }
        return registered.stream().map(RegisteredRedirectUriErrorHandler::parse).filter(Objects::nonNull)
                .anyMatch(uri -> Objects.equals(uri.getScheme(), candidate.getScheme())
                        && Objects.equals(uri.getHost(), candidate.getHost())
                        && Objects.equals(uri.getPath(), candidate.getPath())
                        && Objects.equals(uri.getQuery(), candidate.getQuery())
                        && Objects.equals(uri.getFragment(), candidate.getFragment()));
    }

    private static UriComponents parse(String uri) {
        try {
            return UriComponentsBuilder.fromUriString(uri).build();
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static String errorRedirect(String redirectUri, OAuth2Error error, String state) {
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam(OAuth2ParameterNames.ERROR, error.getErrorCode());
        if (StringUtils.hasText(error.getDescription())) {
            target.queryParam(OAuth2ParameterNames.ERROR_DESCRIPTION,
                    UriUtils.encode(error.getDescription(), StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(error.getUri())) {
            target.queryParam(OAuth2ParameterNames.ERROR_URI, UriUtils.encode(error.getUri(), StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(state)) {
            target.queryParam(OAuth2ParameterNames.STATE, UriUtils.encode(state, StandardCharsets.UTF_8));
        }
        return target.build(true).toUriString();
    }
}
