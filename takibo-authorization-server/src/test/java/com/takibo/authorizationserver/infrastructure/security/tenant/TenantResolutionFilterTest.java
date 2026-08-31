package com.takibo.authorizationserver.infrastructure.security.tenant;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ClientType;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientContextHolder;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Fige le comportement de {@link TenantResolutionFilter} (TAS-GRANTS-01).
 * <p>
 * Le filtre decide, par surface, si un client doit etre resolu, resolu seulement si un indice
 * est fourni, ou ignore :
 * <ul>
 *   <li>{@code /oauth2/token} et {@code /oauth2/authorize} exigent un {@code client_id} ;</li>
 *   <li>{@code /.well-known/*} et {@code /userinfo} ne resolvent que si un indice est fourni ;</li>
 *   <li>{@code /oauth2/introspect} et {@code /oauth2/revoke} exigent une identification.</li>
 * </ul>
 * Deux invariants portants : le {@link ResolvedOAuthClient} est disponible pendant la chaine
 * via {@link ResolvedOAuthClientContextHolder}, et il est purge dans tous les cas, y compris
 * en echec. Un {@link Optional#empty()} du resolveur est fail-closed, ecrit par
 * {@link OAuth2HttpErrorWriter} — la meme forme RFC 6749 que le rejet natif de Spring
 * Authorization Server — et opaque, sans description, pour ne jamais distinguer un client
 * inconnu d'un secret invalide.
 */
@ExtendWith(MockitoExtension.class)
class TenantResolutionFilterTest {

    private static final String CLIENT_ID = "busa-finance";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final UUID ORG_ID = UUID.fromString("674b889c-4d4e-47bd-bdf6-972dc84f1b49");
    private static final UUID SPACE_ID = UUID.fromString("8932f9bc-0af0-4c64-94c8-abb0150c348b");
    private static final ResolvedOAuthClient CLIENT = new ResolvedOAuthClient(
            "registered-" + CLIENT_ID, CLIENT_ID, ClientPlan.SPACE, ORG_ID, SPACE_ID,
            ClientType.CONFIDENTIAL, false, false, true, "hash",
            "client_secret_basic", null, null, null, null, null, null,
            Set.of("api.read"), Set.of("client_credentials"), Set.of(), Set.of());

    @Mock private ResolvedOAuthClientResolver resolvedOAuthClientResolver;
    @Mock private OAuth2HttpErrorWriter errorWriter;
    @Mock private FilterChain chain;

    private final ClientIdExtractor clientIdExtractor = new ClientIdExtractor();

    private TenantResolutionFilter filter() {
        return new TenantResolutionFilter(resolvedOAuthClientResolver, clientIdExtractor, errorWriter);
    }

    @AfterEach
    void clearResolvedClientContext() {
        ResolvedOAuthClientContextHolder.clear();
    }

    // ---------- /oauth2/token ----------

    @Test
    void given_token_request_with_basic_auth_when_filter_then_resolves_and_publishes_client()
            throws Exception {
        when(resolvedOAuthClientResolver.resolve(CLIENT_ID)).thenReturn(Optional.of(CLIENT));
        AtomicReference<ResolvedOAuthClient> seenDuringChain = captureClientDuringChain();

        MockHttpServletRequest request = tokenRequest();
        request.addHeader("Authorization", basic(CLIENT_ID));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        assertThat(seenDuringChain.get()).isEqualTo(CLIENT);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(errorWriter);
    }

    @Test
    void given_token_request_with_form_client_id_when_filter_then_resolves() throws Exception {
        when(resolvedOAuthClientResolver.resolve(CLIENT_ID)).thenReturn(Optional.of(CLIENT));

        MockHttpServletRequest request = tokenRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(resolvedOAuthClientResolver).resolve(CLIENT_ID);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void given_token_request_without_client_id_when_filter_then_invalid_client_and_chain_stops()
            throws Exception {
        MockHttpServletRequest request = tokenRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidClient(isNull(), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(resolvedOAuthClientResolver);
    }

    // ---------- /oauth2/authorize ----------

    @Test
    void given_authorize_request_with_client_id_when_filter_then_resolves() throws Exception {
        when(resolvedOAuthClientResolver.resolve(CLIENT_ID)).thenReturn(Optional.of(CLIENT));

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(resolvedOAuthClientResolver).resolve(CLIENT_ID);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void given_authorize_request_without_client_id_when_filter_then_invalid_request() throws Exception {
        MockHttpServletRequest request = authorizeRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(
                eq("Missing required parameter: client_id"), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(resolvedOAuthClientResolver);
    }

    // ---------- Surfaces a indice optionnel ----------

    @Test
    void given_discovery_request_without_hint_when_filter_then_passes_without_resolution()
            throws Exception {
        MockHttpServletRequest request = uriRequest("/.well-known/oauth-authorization-server");

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verifyNoInteractions(resolvedOAuthClientResolver);
        verifyNoInteractions(errorWriter);
    }

    @Test
    void given_discovery_request_with_hint_when_filter_then_resolves() throws Exception {
        when(resolvedOAuthClientResolver.resolve(CLIENT_ID)).thenReturn(Optional.of(CLIENT));

        MockHttpServletRequest request = uriRequest("/.well-known/openid-configuration");
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(resolvedOAuthClientResolver).resolve(CLIENT_ID);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void given_userinfo_request_without_hint_when_filter_then_passes_without_resolution()
            throws Exception {
        MockHttpServletRequest request = uriRequest("/userinfo");

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verifyNoInteractions(resolvedOAuthClientResolver);
    }

    @Test
    void given_userinfo_request_with_hint_when_filter_then_resolves() throws Exception {
        when(resolvedOAuthClientResolver.resolve(CLIENT_ID)).thenReturn(Optional.of(CLIENT));

        MockHttpServletRequest request = uriRequest("/userinfo");
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(resolvedOAuthClientResolver).resolve(CLIENT_ID);
    }

    // ---------- Surfaces exigeant une identification ----------

    @Test
    void given_introspect_request_without_client_id_when_filter_then_invalid_client()
            throws Exception {
        // RFC 7662 section 2.3 : une authentification cliente absente ou invalide est un 401,
        // distinct du jeton introspecte invalide ou revoque, qui reste un 200 active=false.
        // Opaque, sans description, comme /oauth2/token.
        MockHttpServletRequest request = uriRequest("/oauth2/introspect");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidClient(isNull(), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_revoke_request_without_client_id_when_filter_then_invalid_client()
            throws Exception {
        // RFC 7009 section 2.1 renvoie au modele d'erreurs OAuth 2.0 : meme contrat que
        // l'introspection pour l'authentification cliente.
        MockHttpServletRequest request = uriRequest("/oauth2/revoke");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidClient(isNull(), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_revoke_request_with_client_id_when_filter_then_resolves() throws Exception {
        when(resolvedOAuthClientResolver.resolve(CLIENT_ID)).thenReturn(Optional.of(CLIENT));

        MockHttpServletRequest request = uriRequest("/oauth2/revoke");
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(resolvedOAuthClientResolver).resolve(CLIENT_ID);
        verify(chain).doFilter(any(), any());
    }

    // ---------- Echecs de resolution ----------

    @Test
    void given_unknown_client_when_filter_then_invalid_client_without_a_description() throws Exception {
        when(resolvedOAuthClientResolver.resolve(CLIENT_ID)).thenReturn(Optional.empty());

        MockHttpServletRequest request = tokenRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        // Opaque : aucune description, pour ne jamais distinguer un client inconnu d'un
        // secret invalide, refuse par ailleurs par Spring Authorization Server lui-meme.
        verify(errorWriter).writeInvalidClient(isNull(), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_unknown_client_at_authorize_when_filter_then_invalid_request_without_basic_challenge()
            throws Exception {
        // RFC 6749 4.1.2.1 : invalid_client n'existe pas au point de terminaison authorize.
        // Un WWW-Authenticate: Basic y provoquerait en plus une invite de identifiants
        // native cote navigateur, qui n'authentifie jamais de client HTTP a cette surface.
        when(resolvedOAuthClientResolver.resolve(CLIENT_ID)).thenReturn(Optional.empty());

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(
                eq("Invalid parameter: client_id"), eq(request), eq(response));
        verify(errorWriter, never()).writeInvalidClient(any(), any(), any());
        verify(chain, never()).doFilter(any(), any());
    }

    // ---------- Purge du contexte ----------

    @Test
    void given_successful_request_when_filter_completes_then_resolved_client_context_is_cleared()
            throws Exception {
        when(resolvedOAuthClientResolver.resolve(CLIENT_ID)).thenReturn(Optional.of(CLIENT));

        MockHttpServletRequest request = tokenRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(ResolvedOAuthClientContextHolder.get()).isNull();
    }

    @Test
    void given_failing_request_when_filter_completes_then_resolved_client_context_is_cleared()
            throws Exception {
        when(resolvedOAuthClientResolver.resolve(CLIENT_ID)).thenReturn(Optional.empty());

        MockHttpServletRequest request = tokenRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(ResolvedOAuthClientContextHolder.get()).isNull();
    }

    // ---------- Fixtures ----------

    private AtomicReference<ResolvedOAuthClient> captureClientDuringChain() throws Exception {
        AtomicReference<ResolvedOAuthClient> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            captured.set(ResolvedOAuthClientContextHolder.get());
            return null;
        }).when(chain).doFilter(any(), any());
        return captured;
    }

    private static MockHttpServletRequest tokenRequest() {
        return uriRequest("/oauth2/token", "POST");
    }

    private static MockHttpServletRequest authorizeRequest() {
        return uriRequest("/oauth2/authorize", "GET");
    }

    private static MockHttpServletRequest uriRequest(String uri) {
        return uriRequest(uri, "GET");
    }

    private static MockHttpServletRequest uriRequest(String uri, String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private static String basic(String clientId) {
        String raw = clientId + ":secret";
        return "Basic " + Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
