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
                .redirectUri("http://[::1]/native")
                .redirectUri("http://localhost/native")
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
    void an_ipv6_loopback_uri_matches_its_registration_whatever_the_port() throws Exception {
        assertThat(fail("web-client", "http://[::1]:51004/native").getStatus()).isEqualTo(302);
        assertThat(fail("web-client", "http://[::1]:51004/other").getStatus()).isEqualTo(400);
    }

    @Test
    void a_loopback_host_name_is_not_a_loopback_address_and_keeps_its_port() throws Exception {
        assertThat(fail("web-client", "http://localhost/native").getStatus()).isEqualTo(302);
        assertThat(fail("web-client", "http://localhost:51004/native").getStatus()).isEqualTo(400);
    }

    @Test
    void encoded_or_case_variants_of_a_registered_uri_are_not_registered() throws Exception {
        for (String variant : new String[]{"https://app.test/%63allback", "https://app.test/callback%2F..%2Fphish",
                "HTTPS://APP.TEST/callback", "https://app.test/callback%20", "https://app.test/callback/.",
                "https://app.test:443/callback", "https://app.test/callback?", "https://app.test/callback#x"}) {
            assertWrittenOnTheSpot(fail("web-client", variant), "unauthorized_client");
        }
    }

    @Test
    void a_hostile_state_is_encoded_and_cannot_inject_parameters() throws Exception {
        String hostile = "x&error=access_denied&redirect_uri=https://redirect-probe.invalid#frag";
        MockHttpServletResponse response = failWithState("web-client", REGISTERED, hostile);

        java.net.URI location = java.net.URI.create(response.getRedirectedUrl());
        assertThat(location.getHost()).isEqualTo("app.test");
        assertThat(location.getRawFragment()).isNull();
        var query = org.springframework.web.util.UriComponentsBuilder.fromUri(location).build(true).getQueryParams();
        assertThat(query.get("error")).containsExactly("unauthorized_client");
        assertThat(query).doesNotContainKey("redirect_uri");
        assertThat(java.net.URLDecoder.decode(query.getFirst("state"), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(hostile);
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
        return failWithState(clientId, redirectUri, "xyz");
    }

    private MockHttpServletResponse failWithState(String clientId, String redirectUri, String state) throws Exception {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = new OAuth2AuthorizationCodeRequestAuthenticationToken(
                "http://localhost/oauth2/authorize", clientId,
                new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")),
                redirectUri, state, Set.of(), Map.of());
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
