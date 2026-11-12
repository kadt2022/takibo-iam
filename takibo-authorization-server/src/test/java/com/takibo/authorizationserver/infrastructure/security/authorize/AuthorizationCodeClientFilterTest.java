package com.takibo.authorizationserver.infrastructure.security.authorize;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ClientType;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientContextHolder;
import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthorizationCodeClientFilterTest {

    private static final UUID ORG_ID = UUID.fromString("674b889c-4d4e-47bd-bdf6-972dc84f1b49");
    private static final UUID SPACE_ID = UUID.fromString("8932f9bc-0af0-4c64-94c8-abb0150c348b");

    @Mock private OAuth2HttpErrorWriter errorWriter;
    @Mock private FilterChain chain;

    @AfterEach
    void clearContext() {
        ResolvedOAuthClientContextHolder.clear();
    }

    @Test
    void a_machine_client_is_refused_with_unauthorized_client_and_never_reaches_the_authorization_server()
            throws Exception {
        givenClient(Set.of("client_credentials"), Set.of());
        MockHttpServletRequest request = authorize();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AuthorizationCodeClientFilter(errorWriter).doFilter(request, response, chain);

        ArgumentCaptor<OAuth2Error> error = ArgumentCaptor.forClass(OAuth2Error.class);
        verify(errorWriter).writeAuthorizationError(error.capture(), eq(request), eq(response));
        assertThat(error.getValue().getErrorCode()).isEqualTo("unauthorized_client");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void a_client_with_authorization_code_but_no_registered_uri_is_refused() throws Exception {
        givenClient(Set.of("authorization_code"), Set.of());
        MockHttpServletRequest request = authorize();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AuthorizationCodeClientFilter(errorWriter).doFilter(request, response, chain);

        verify(errorWriter).writeAuthorizationError(any(), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void a_web_client_continues_to_the_authorization_server_unchanged() throws Exception {
        givenClient(Set.of("authorization_code", "refresh_token"), Set.of("https://app.test/cb"));
        MockHttpServletRequest request = authorize();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AuthorizationCodeClientFilter(errorWriter).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(errorWriter);
    }

    @Test
    void without_a_resolved_client_the_request_is_refused_closed() throws Exception {
        MockHttpServletRequest request = authorize();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AuthorizationCodeClientFilter(errorWriter).doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(eq("Tenant context not set"), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void other_endpoints_are_not_concerned() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setRequestURI("/oauth2/token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AuthorizationCodeClientFilter(errorWriter).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(errorWriter);
    }

    private static void givenClient(Set<String> grants, Set<String> redirectUris) {
        ResolvedOAuthClientContextHolder.set(new ResolvedOAuthClient(
                "registered-id", "client-a", ClientPlan.SPACE, ORG_ID, SPACE_ID,
                ClientType.CONFIDENTIAL, false, false, true, "hash", "client_secret_basic",
                null, null, null, null, null, null,
                Set.of("api.read"), grants, redirectUris, Set.of()));
    }

    private static MockHttpServletRequest authorize() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setRequestURI("/oauth2/authorize");
        request.setParameter("client_id", "client-a");
        request.setParameter("redirect_uri", "https://redirect-probe.invalid/phish");
        return request;
    }
}
