package com.takibo.authorizationserver.infrastructure.security.tenant;

import com.takibo.authorizationserver.domain.exception.TakiboInvalidClientException;
import com.takibo.authorizationserver.domain.exception.TakiboInvalidRequestException;
import com.takibo.authorizationserver.domain.exception.TakiboServerErrorException;
import com.takibo.authorizationserver.domain.exception.TenantNotFoundException;
import com.takibo.authorizationserver.domain.security.tenant.TenantContext;
import com.takibo.authorizationserver.domain.security.tenant.TenantContextHolder;
import com.takibo.authorizationserver.domain.security.tenant.TenantResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Fige le comportement de {@link TenantResolutionFilter} (TAS-GRANTS-00).
 * <p>
 * Le filtre decide, par surface, si un tenant est exige, optionnel ou ignore. Le recit 01
 * remplacera l'implementation du {@link TenantResolver}, pas cette repartition :
 * <ul>
 *   <li>{@code /oauth2/token} et {@code /oauth2/authorize} exigent un {@code client_id} ;</li>
 *   <li>{@code /.well-known/*} et {@code /userinfo} ne resolvent que si un indice est fourni ;</li>
 *   <li>{@code /oauth2/introspect} et {@code /oauth2/revoke} exigent une identification.</li>
 * </ul>
 * Deux invariants portants : le contexte est disponible pendant la chaine, et il est purge
 * dans tous les cas, y compris en echec.
 */
@ExtendWith(MockitoExtension.class)
class TenantResolutionFilterTest {

    private static final String CLIENT_ID = "busa-finance";
    private static final UUID ORG_ID = UUID.fromString("674b889c-4d4e-47bd-bdf6-972dc84f1b49");
    private static final UUID SPACE_ID = UUID.fromString("8932f9bc-0af0-4c64-94c8-abb0150c348b");
    private static final TenantContext TENANT = new TenantContext(ORG_ID, SPACE_ID);

    @Mock private TenantResolver tenantResolver;
    @Mock private HandlerExceptionResolver handlerExceptionResolver;
    @Mock private FilterChain chain;

    private final ClientIdExtractor clientIdExtractor = new ClientIdExtractor();

    private TenantResolutionFilter filter() {
        return new TenantResolutionFilter(tenantResolver, clientIdExtractor, handlerExceptionResolver);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    // ---------- /oauth2/token ----------

    @Test
    void given_token_request_with_basic_auth_when_filter_then_resolves_and_publishes_tenant()
            throws Exception {
        when(tenantResolver.resolve(CLIENT_ID)).thenReturn(TENANT);
        AtomicReference<TenantContext> seenDuringChain = captureTenantDuringChain();

        MockHttpServletRequest request = tokenRequest();
        request.addHeader("Authorization", basic(CLIENT_ID));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        assertThat(seenDuringChain.get()).isEqualTo(TENANT);
        verify(chain).doFilter(request, response);
    }

    @Test
    void given_token_request_with_form_client_id_when_filter_then_resolves() throws Exception {
        when(tenantResolver.resolve(CLIENT_ID)).thenReturn(TENANT);

        MockHttpServletRequest request = tokenRequest();
        request.setParameter("client_id", CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(tenantResolver).resolve(CLIENT_ID);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void given_token_request_without_client_id_when_filter_then_invalid_client_and_chain_stops()
            throws Exception {
        givenExceptionIsHandled();

        MockHttpServletRequest request = tokenRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(handlerExceptionResolver).resolveException(
                any(), any(), isNull(), any(TakiboInvalidClientException.class));
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(tenantResolver);
    }

    // ---------- /oauth2/authorize ----------

    @Test
    void given_authorize_request_with_client_id_when_filter_then_resolves() throws Exception {
        when(tenantResolver.resolve(CLIENT_ID)).thenReturn(TENANT);

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter("client_id", CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(tenantResolver).resolve(CLIENT_ID);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void given_authorize_request_without_client_id_when_filter_then_invalid_request() throws Exception {
        givenExceptionIsHandled();

        MockHttpServletRequest request = authorizeRequest();

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(handlerExceptionResolver).resolveException(
                any(), any(), isNull(), any(TakiboInvalidRequestException.class));
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(tenantResolver);
    }

    // ---------- Surfaces a indice optionnel ----------

    @Test
    void given_discovery_request_without_hint_when_filter_then_passes_without_resolution()
            throws Exception {
        MockHttpServletRequest request = uriRequest("/.well-known/oauth-authorization-server");

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verifyNoInteractions(tenantResolver);
        verifyNoInteractions(handlerExceptionResolver);
    }

    @Test
    void given_discovery_request_with_hint_when_filter_then_resolves() throws Exception {
        when(tenantResolver.resolve(CLIENT_ID)).thenReturn(TENANT);

        MockHttpServletRequest request = uriRequest("/.well-known/openid-configuration");
        request.setParameter("client_id", CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(tenantResolver).resolve(CLIENT_ID);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void given_userinfo_request_without_hint_when_filter_then_passes_without_resolution()
            throws Exception {
        MockHttpServletRequest request = uriRequest("/userinfo");

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verifyNoInteractions(tenantResolver);
    }

    @Test
    void given_userinfo_request_with_hint_when_filter_then_resolves() throws Exception {
        when(tenantResolver.resolve(CLIENT_ID)).thenReturn(TENANT);

        MockHttpServletRequest request = uriRequest("/userinfo");
        request.setParameter("client_id", CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(tenantResolver).resolve(CLIENT_ID);
    }

    // ---------- Surfaces exigeant une identification ----------

    @Test
    void given_introspect_request_without_client_id_when_filter_then_invalid_request()
            throws Exception {
        givenExceptionIsHandled();

        MockHttpServletRequest request = uriRequest("/oauth2/introspect");

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(handlerExceptionResolver).resolveException(
                any(), any(), isNull(), any(TakiboInvalidRequestException.class));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_revoke_request_with_client_id_when_filter_then_resolves() throws Exception {
        when(tenantResolver.resolve(CLIENT_ID)).thenReturn(TENANT);

        MockHttpServletRequest request = uriRequest("/oauth2/revoke");
        request.setParameter("client_id", CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(tenantResolver).resolve(CLIENT_ID);
        verify(chain).doFilter(any(), any());
    }

    // ---------- Echecs de resolution ----------

    @Test
    void given_unknown_tenant_when_filter_then_invalid_client() throws Exception {
        when(tenantResolver.resolve(CLIENT_ID))
                .thenThrow(new TenantNotFoundException("client " + CLIENT_ID));
        givenExceptionIsHandled();

        MockHttpServletRequest request = tokenRequest();
        request.setParameter("client_id", CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(handlerExceptionResolver).resolveException(
                any(), any(), isNull(), any(TakiboInvalidClientException.class));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_resolution_system_failure_when_filter_then_server_error() throws Exception {
        when(tenantResolver.resolve(CLIENT_ID))
                .thenThrow(new TakiboServerErrorException.TenantResolutionException("TMS down"));
        givenExceptionIsHandled();

        MockHttpServletRequest request = tokenRequest();
        request.setParameter("client_id", CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(handlerExceptionResolver).resolveException(
                any(), any(), isNull(), any(TakiboServerErrorException.class));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_unhandled_exception_and_uncommitted_response_when_filter_then_rethrows() {
        when(tenantResolver.resolve(CLIENT_ID))
                .thenThrow(new TenantNotFoundException("client " + CLIENT_ID));
        when(handlerExceptionResolver.resolveException(any(), any(), isNull(), any()))
                .thenReturn(null);

        MockHttpServletRequest request = tokenRequest();
        request.setParameter("client_id", CLIENT_ID);

        assertThatThrownBy(() -> filter().doFilter(request, new MockHttpServletResponse(), chain))
                .isInstanceOf(TakiboInvalidClientException.class);
    }

    // ---------- Purge du contexte ----------

    @Test
    void given_successful_request_when_filter_completes_then_tenant_context_is_cleared()
            throws Exception {
        when(tenantResolver.resolve(CLIENT_ID)).thenReturn(TENANT);

        MockHttpServletRequest request = tokenRequest();
        request.setParameter("client_id", CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void given_failing_request_when_filter_completes_then_tenant_context_is_cleared()
            throws Exception {
        when(tenantResolver.resolve(CLIENT_ID))
                .thenThrow(new TenantNotFoundException("client " + CLIENT_ID));
        givenExceptionIsHandled();

        MockHttpServletRequest request = tokenRequest();
        request.setParameter("client_id", CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(TenantContextHolder.get()).isNull();
    }

    // ---------- Fixtures ----------

    private void givenExceptionIsHandled() {
        when(handlerExceptionResolver.resolveException(any(), any(), isNull(), any()))
                .thenReturn(new ModelAndView());
    }

    private AtomicReference<TenantContext> captureTenantDuringChain() throws Exception {
        AtomicReference<TenantContext> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            captured.set(TenantContextHolder.get());
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
