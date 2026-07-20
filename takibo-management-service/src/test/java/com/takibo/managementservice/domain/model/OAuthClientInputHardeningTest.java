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
        assertThatThrownBy(() -> ClientGrantType.ofAll(Set.of("password")))
                .isInstanceOf(InvalidGrantTypeException.class);
        assertThatThrownBy(() -> ClientGrantType.ofAll(Set.of("urn:example:custom-grant")))
                .isInstanceOf(InvalidGrantTypeException.class);
        assertThatThrownBy(() -> ClientGrantType.ofAll(Set.of(" ")))
                .isInstanceOf(InvalidGrantTypeException.class);

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
        assertThatThrownBy(() -> ClientPostLogoutRedirectUri.ofAll(Set.of("http://app.example/logout")))
                .isInstanceOf(InvalidPostLogoutRedirectUriException.class);
        assertThat(ClientPostLogoutRedirectUri.ofAll(Set.of("https://app.example/logout")))
                .singleElement();
    }

    @Test
    void cors_origins_require_https_outside_loopback() {
        assertThatThrownBy(() -> ClientCorsOrigin.ofAll(Set.of("http://app.example")))
                .isInstanceOf(InvalidCorsOriginException.class);
        assertThatThrownBy(() -> ClientCorsOrigin.ofAll(Set.of("https://user@app.example")))
                .isInstanceOf(InvalidCorsOriginException.class);
        assertThatThrownBy(() -> ClientCorsOrigin.ofAll(Set.of("https://app.example/path")))
                .isInstanceOf(InvalidCorsOriginException.class);
        assertThatThrownBy(() -> ClientCorsOrigin.ofAll(Set.of("https://app.example?query=true")))
                .isInstanceOf(InvalidCorsOriginException.class);
        assertThatThrownBy(() -> ClientCorsOrigin.ofAll(Set.of("https://app.example#fragment")))
                .isInstanceOf(InvalidCorsOriginException.class);

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
        assertThatThrownBy(() -> ClientRedirectUri.ofAll(Set.of(uri)))
                .isInstanceOf(InvalidRedirectUriException.class);
    }
}
