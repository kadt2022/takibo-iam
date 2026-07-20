package com.takibo.managementservice.domain.model;

import com.takibo.managementservice.domain.exception.InvalidCorsOriginException;
import com.takibo.managementservice.domain.exception.InvalidGrantTypeException;
import com.takibo.managementservice.domain.exception.InvalidPostLogoutRedirectUriException;
import com.takibo.managementservice.domain.exception.InvalidRedirectUriException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientInputHardeningTest {

    @Test
    void grant_types_use_an_explicit_allowlist() {
        assertInvalidGrantType("password");
        assertInvalidGrantType("urn:example:custom-grant");
        assertInvalidGrantType(" ");

        assertThat(ClientGrantType.ofAll(Set.of("urn:ietf:params:oauth:grant-type:device_code")))
                .singleElement()
                .extracting(ClientGrantType::getValue)
                .isEqualTo("urn:ietf:params:oauth:grant-type:device_code");
    }

    @Test
    void redirect_uris_require_https_outside_loopback_and_reject_fragments_or_user_info() {
        assertInvalidRedirect("http://app.example/callback");
        assertInvalidRedirect("http://127.evil.example/callback");
        assertInvalidRedirect("http://127.0.0.999/callback");
        assertInvalidRedirect("https://app.example/callback#fragment");
        assertInvalidRedirect("https://user@app.example/callback");

        assertThat(ClientRedirectUri.ofAll(Set.of("http://127.0.0.1:8080/callback")))
                .singleElement();
        assertThat(ClientRedirectUri.ofAll(Set.of("http://localhost:8080/callback")))
                .singleElement();
        assertThat(ClientRedirectUri.ofAll(Set.of("http://[::1]:8080/callback")))
                .singleElement();
        assertThat(ClientRedirectUri.ofAll(Set.of("https://app.example/callback")))
                .singleElement();
    }

    @Test
    void post_logout_redirects_apply_the_same_transport_policy() {
        assertInvalidPostLogoutRedirect("http://app.example/logout");
        assertThat(ClientPostLogoutRedirectUri.ofAll(Set.of("https://app.example/logout")))
                .singleElement();
    }

    @Test
    void cors_origins_require_https_outside_loopback() {
        assertInvalidCorsOrigin("http://app.example");
        assertInvalidCorsOrigin("https://user@app.example");
        assertInvalidCorsOrigin("https://app.example/path");
        assertInvalidCorsOrigin("https://app.example?query=true");
        assertInvalidCorsOrigin("https://app.example#fragment");

        assertThat(ClientCorsOrigin.ofAll(Set.of("http://localhost:3000")))
                .singleElement()
                .extracting(ClientCorsOrigin::getOrigin)
                .isEqualTo("http://localhost:3000");
        assertThat(ClientCorsOrigin.ofAll(Set.of("HTTPS://APP.EXAMPLE/")))
                .singleElement()
                .extracting(ClientCorsOrigin::getOrigin)
                .isEqualTo("https://app.example");
    }

    private static void assertInvalidRedirect(String uri) {
        Set<String> values = Set.of(uri);
        assertThatThrownBy(() -> ClientRedirectUri.ofAll(values))
                .isInstanceOf(InvalidRedirectUriException.class);
    }

    private static void assertInvalidGrantType(String grantType) {
        Set<String> values = Set.of(grantType);
        assertThatThrownBy(() -> ClientGrantType.ofAll(values))
                .isInstanceOf(InvalidGrantTypeException.class);
    }

    private static void assertInvalidPostLogoutRedirect(String uri) {
        Set<String> values = Set.of(uri);
        assertThatThrownBy(() -> ClientPostLogoutRedirectUri.ofAll(values))
                .isInstanceOf(InvalidPostLogoutRedirectUriException.class);
    }

    private static void assertInvalidCorsOrigin(String origin) {
        Set<String> values = Set.of(origin);
        assertThatThrownBy(() -> ClientCorsOrigin.ofAll(values))
                .isInstanceOf(InvalidCorsOriginException.class);
    }
}
