package com.takibo.managementservice.domain.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class UriValidationTest {

    // ---------------------------------------------------------------
    // URL http(s) de base
    // ---------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void a_blank_value_is_refused(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UriValidation.requireHttpHttpsUrl(value))
                .withMessage("URI value is blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/relative/callback",
            "ftp://files.example/cb",
            "javascript:alert(1)",
            "mailto:admin@example.com",
            "com.example.app:/oauth2redirect"
    })
    void only_the_http_and_https_schemes_are_allowed(String value) {
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireHttpHttpsUrl(value))
                .withMessage("Only http/https scheme is allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https:///callback", "https://*.example.com/callback"})
    void a_host_is_required(String value) {
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireHttpHttpsUrl(value))
                .withMessage("Host is required");
    }

    @Test
    void surrounding_spaces_are_ignored_and_the_scheme_is_case_insensitive() {
        assertThat(UriValidation.requireHttpHttpsUrl("  HTTPS://app.example/cb  "))
                .hasToString("HTTPS://app.example/cb");
    }

    // ---------------------------------------------------------------
    // Redirect URI OAuth
    // ---------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/cb",
            "http://LOCALHOST:8080/cb",
            "http://127.0.0.1/cb",
            "http://127.255.255.254:9000/cb",
            "http://[::1]:8080/cb"
    })
    void plain_http_is_tolerated_for_loopback_hosts_only(String value) {
        assertThat(UriValidation.requireOAuthRedirectUrl(value)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://app.example/cb",
            "http://localhost.evil.example/cb",
            "http://127.0.0.1.evil.example/cb",
            "http://127.0.0/cb",
            "http://127.0.0.256/cb",
            "http://127.0.0.0001/cb",
            "http://128.0.0.1/cb",
            "http://10.0.0.1/cb",
            "http://[0:0:0:0:0:0:0:1]/cb"
    })
    void plain_http_to_any_other_host_is_refused(String value) {
        // Selon la forme, java.net.URI rejette déjà l'hôte ("Host is required")
        // ou la règle loopback refuse le transport : dans tous les cas, refus.
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireOAuthRedirectUrl(value))
                .withMessageMatching("HTTP is only allowed for loopback hosts|Host is required");
    }

    @Test
    void a_public_host_over_plain_http_is_refused_by_the_loopback_rule() {
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireOAuthRedirectUrl("http://app.example/cb"))
                .withMessage("HTTP is only allowed for loopback hosts");
    }

    @Test
    void a_redirect_may_carry_a_query_but_not_a_fragment_or_credentials() {
        assertThat(UriValidation.requireOAuthRedirectUrl("https://app.example/cb?tenant=a"))
                .hasToString("https://app.example/cb?tenant=a");
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireOAuthRedirectUrl("https://app.example/cb#token"))
                .withMessage("Redirect URI must not include a fragment");
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireOAuthRedirectUrl("https://admin@app.example/cb"))
                .withMessage("Redirect URI must not include user-info");
    }

    // ---------------------------------------------------------------
    // Endpoint HTTPS (jwks_uri)
    // ---------------------------------------------------------------

    @Test
    void an_endpoint_requires_https_even_on_loopback() {
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireHttpsEndpoint("http://localhost/jwks"))
                .withMessage("HTTPS is required");
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireHttpsEndpoint("https://u:p@keys.example/jwks"))
                .withMessage("Endpoint must not include user-info");
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireHttpsEndpoint("https://keys.example/jwks#k1"))
                .withMessage("Endpoint must not include a fragment");
        assertThat(UriValidation.requireHttpsEndpoint("https://keys.example/jwks?v=2"))
                .hasToString("https://keys.example/jwks?v=2");
    }

    // ---------------------------------------------------------------
    // Origin CORS
    // ---------------------------------------------------------------

    @Test
    void an_origin_is_normalized_and_keeps_an_explicit_port() {
        assertThat(UriValidation.requireHttpHttpsOrigin("HTTPS://App.Example:8443/"))
                .hasToString("https://app.example:8443");
        assertThat(UriValidation.requireHttpHttpsOrigin("http://127.0.0.1:3000"))
                .hasToString("http://127.0.0.1:3000");
    }

    @Test
    void an_origin_is_only_scheme_host_and_port() {
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireHttpHttpsOrigin("https://app.example/app"))
                .withMessage("Origin must not include a path");
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireHttpHttpsOrigin("https://app.example?x=1"))
                .withMessage("Origin must not include a query");
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireHttpHttpsOrigin("https://app.example#x"))
                .withMessage("Origin must not include a fragment");
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireHttpHttpsOrigin("https://u@app.example"))
                .withMessage("Origin must not include user-info");
        assertThatIllegalStateException()
                .isThrownBy(() -> UriValidation.requireHttpHttpsOrigin("http://app.example"))
                .withMessage("HTTP is only allowed for loopback hosts");
    }
}
