package com.takibo.authorizationserver.infrastructure.springauthserver.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RegisteredRedirectUriErrorHandlerTest {

    private static final String REGISTERED = "https://app.test/callback";
    private static final String ATTACKER = "https://redirect-probe.invalid/phish";

    @Mock private RegisteredClientRepository registeredClients;

    private RegisteredRedirectUriErrorHandler handler() {
        lenient().when(registeredClients.findByClientId("web-client")).thenReturn(RegisteredClient.withId("id")
                .clientId("web-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REGISTERED)
                .redirectUri("http://127.0.0.1/native")
                .build());
        return new RegisteredRedirectUriErrorHandler(registeredClients, new OAuth2HttpErrorWriter(new ObjectMapper()));
    }

    @Test
    void an_error_is_never_redirected_to_an_unregistered_uri() throws Exception {
        MockHttpServletResponse response = fail("web-client", ATTACKER);

        assertWrittenOnTheSpot(response, "unauthorized_client");
    }

    @Test
    void an_error_for_an_unknown_client_is_never_redirected() throws Exception {
        MockHttpServletResponse response = fail("unknown-client", ATTACKER);

        assertWrittenOnTheSpot(response, "unauthorized_client");
    }

    @Test
    void an_error_without_redirect_uri_is_written_on_the_spot() throws Exception {
        MockHttpServletResponse response = fail("web-client", null);

        assertWrittenOnTheSpot(response, "unauthorized_client");
    }

    @Test
    void an_exception_that_is_not_an_oauth2_error_is_written_on_the_spot() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler().onAuthenticationFailure(new MockHttpServletRequest(), response, new BadCredentialsException("x"));

        assertWrittenOnTheSpot(response, "invalid_request");
    }

    @Test
    void an_error_is_redirected_to_a_registered_uri_with_its_state() throws Exception {
        MockHttpServletResponse response = fail("web-client", REGISTERED);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .startsWith(REGISTERED + "?error=unauthorized_client")
                .contains("state=xyz");
    }

    @Test
    void a_loopback_uri_matches_its_registration_whatever_the_port() throws Exception {
        assertThat(fail("web-client", "http://127.0.0.1:51004/native").getStatus()).isEqualTo(302);
        assertThat(fail("web-client", "http://127.0.0.1:51004/other").getStatus()).isEqualTo(400);
    }

    @Test
    void a_variant_of_a_registered_uri_is_not_registered() {
        Set<String> registered = Set.of(REGISTERED);

        assertThat(RegisteredRedirectUriErrorHandler.matchesOneOf(registered, REGISTERED + "/")).isFalse();
        assertThat(RegisteredRedirectUriErrorHandler.matchesOneOf(registered, REGISTERED + "?next=x")).isFalse();
        assertThat(RegisteredRedirectUriErrorHandler.matchesOneOf(registered, "https://app.test:8443/callback")).isFalse();
        assertThat(RegisteredRedirectUriErrorHandler.matchesOneOf(registered, "http://app.test/callback")).isFalse();
    }

    private MockHttpServletResponse fail(String clientId, String redirectUri) throws Exception {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = new OAuth2AuthorizationCodeRequestAuthenticationToken(
                "http://localhost/oauth2/authorize", clientId,
                new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")),
                redirectUri, "xyz", Set.of(), Map.of());
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler().onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthorizationCodeRequestAuthenticationException(
                        new OAuth2Error("unauthorized_client", "not allowed", null), request));
        return response;
    }

    private static void assertWrittenOnTheSpot(MockHttpServletResponse response, String error) throws Exception {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getHeader("Location")).isNull();
        assertThat(response.getContentAsString()).contains("\"error\":\"" + error + "\"").doesNotContain("redirect-probe");
    }
}
