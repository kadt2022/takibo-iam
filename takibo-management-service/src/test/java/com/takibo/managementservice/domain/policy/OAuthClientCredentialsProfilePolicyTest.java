package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientCredentialsProfilePolicyTest {

    private final OAuthClientCredentialsProfilePolicy policy =
            new OAuthClientCredentialsProfilePolicy();

    @Test
    void normalizes_client_credentials_into_a_confidential_profile() {
        OAuthClientRegistration normalized =
                policy.normalizeAndValidate(registration(
                        Set.of("client_credentials"),
                        Set.of(),
                        Set.of()
                ));

        assertThat(normalized.clientType())
                .isEqualTo(ClientType.CONFIDENTIAL);
        assertThat(normalized.tokenEndpointAuthMethod())
                .isEqualTo(TokenEndpointAuthMethod.client_secret_basic);
        assertThat(normalized.requireClientSecret()).isTrue();
        assertThat(normalized.requirePkce()).isFalse();
        assertThat(normalized.requireConsent()).isFalse();
    }

    @Test
    void rejects_mixed_client_credentials_grants() {
        OAuthClientRegistration registration = registration(
                Set.of("client_credentials", "authorization_code"),
                Set.of(),
                Set.of()
        );

        assertThatThrownBy(() -> policy.normalizeAndValidate(registration))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage(
                        "client_credentials cannot be combined with other grant types"
                );
    }

    @Test
    void rejects_redirects_for_client_credentials() {
        OAuthClientRegistration registration = registration(
                Set.of("client_credentials"),
                Set.of("https://app.example/callback"),
                Set.of()
        );

        assertThatThrownBy(() -> policy.normalizeAndValidate(registration))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage(
                        "client_credentials must not include "
                                + "redirect/cors/post-logout URIs"
                );
    }

    @Test
    void leaves_a_registration_without_grant_types_untouched() {
        OAuthClientRegistration registration = registration(null, Set.of(), Set.of());

        assertThat(policy.normalizeAndValidate(registration)).isSameAs(registration);
    }

    @Test
    void leaves_an_interactive_registration_untouched() {
        OAuthClientRegistration registration = registration(
                Set.of("authorization_code", "refresh_token"),
                Set.of("https://app.example/callback"),
                Set.of("https://app.example")
        );

        assertThat(policy.normalizeAndValidate(registration)).isSameAs(registration);
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenEndpointAuthMethod.class,
            names = "none",
            mode = EnumSource.Mode.EXCLUDE
    )
    void keeps_an_explicit_client_authentication_method(
            TokenEndpointAuthMethod method
    ) {
        OAuthClientRegistration normalized = policy.normalizeAndValidate(
                registration(Set.of("client_credentials"), Set.of(), Set.of(), method)
        );

        assertThat(normalized.tokenEndpointAuthMethod()).isEqualTo(method);
    }

    @Test
    void defaults_a_missing_authentication_method_to_basic_secret() {
        OAuthClientRegistration normalized = policy.normalizeAndValidate(
                registration(Set.of("client_credentials"), Set.of(), Set.of(), null)
        );

        assertThat(normalized.tokenEndpointAuthMethod())
                .isEqualTo(TokenEndpointAuthMethod.client_secret_basic);
    }

    @Test
    void preserves_identity_token_and_key_settings_while_clearing_browser_uris() {
        OAuthClientRegistration source = new OAuthClientRegistration(
                "machine", "Machine", ClientType.CONFIDENTIAL, false,
                TokenEndpointAuthMethod.client_secret_post, true, true,
                "https://keys.example/jwks", null, "PS256",
                600, 7200, 300, java.time.Instant.parse("2031-01-01T00:00:00Z"),
                Set.of("api:write"), Set.of("client_credentials"),
                null, null, null
        );

        OAuthClientRegistration normalized = policy.normalizeAndValidate(source);

        assertThat(normalized.clientId()).isEqualTo("machine");
        assertThat(normalized.clientName()).isEqualTo("Machine");
        assertThat(normalized.jwksUri()).isEqualTo("https://keys.example/jwks");
        assertThat(normalized.jwksJson()).isNull();
        assertThat(normalized.idTokenSignedAlg()).isEqualTo("PS256");
        assertThat(normalized.accessTokenTtlSeconds()).isEqualTo(600);
        assertThat(normalized.refreshTokenTtlSeconds()).isEqualTo(7200);
        assertThat(normalized.idTokenTtlSeconds()).isEqualTo(300);
        assertThat(normalized.clientSecretExpiresAt())
                .isEqualTo(java.time.Instant.parse("2031-01-01T00:00:00Z"));
        assertThat(normalized.scopes()).containsExactly("api:write");
        assertThat(normalized.grantTypes()).containsExactly("client_credentials");
        assertThat(normalized.requireClientSecret()).isTrue();
        assertThat(normalized.requirePkce()).isFalse();
        assertThat(normalized.requireConsent()).isFalse();
        assertThat(normalized.redirectUris()).isEmpty();
        assertThat(normalized.postLogoutRedirectUris()).isEmpty();
        assertThat(normalized.corsOrigins()).isEmpty();
    }

    @Test
    void rejects_post_logout_redirects_for_client_credentials() {
        OAuthClientRegistration registration = new OAuthClientRegistration(
                "client-test", "Client Test", ClientType.CONFIDENTIAL, true,
                TokenEndpointAuthMethod.client_secret_basic, false, false,
                null, null, "RS256", 900, 3600, 900, null,
                Set.of("api:read"), Set.of("client_credentials"),
                Set.of(), Set.of("https://app.example/logout"), Set.of()
        );

        assertThatThrownBy(() -> policy.normalizeAndValidate(registration))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage(
                        "client_credentials must not include "
                                + "redirect/cors/post-logout URIs"
                );
    }

    @Test
    void rejects_cors_origins_for_client_credentials() {
        OAuthClientRegistration registration = registration(
                Set.of("client_credentials"),
                Set.of(),
                Set.of("https://app.example")
        );

        assertThatThrownBy(() -> policy.normalizeAndValidate(registration))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage(
                        "client_credentials must not include "
                                + "redirect/cors/post-logout URIs"
                );
    }

    @Test
    void detects_a_mixed_client_credentials_grant_whatever_its_case_or_spacing() {
        OAuthClientRegistration registration = registration(
                Set.of(" Client_Credentials ", "authorization_code"),
                Set.of(),
                Set.of()
        );

        assertThatThrownBy(() -> policy.normalizeAndValidate(registration))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage(
                        "client_credentials cannot be combined with other grant types"
                );
    }

    private static OAuthClientRegistration registration(
            Set<String> grantTypes,
            Set<String> redirectUris,
            Set<String> corsOrigins
    ) {
        return registration(
                grantTypes, redirectUris, corsOrigins, TokenEndpointAuthMethod.none
        );
    }

    private static OAuthClientRegistration registration(
            Set<String> grantTypes,
            Set<String> redirectUris,
            Set<String> corsOrigins,
            TokenEndpointAuthMethod method
    ) {
        return new OAuthClientRegistration(
                "client-test",
                "Client Test",
                ClientType.PUBLIC,
                false,
                method,
                true,
                true,
                null,
                null,
                "RS256",
                900,
                3600,
                900,
                null,
                Set.of("api:read"),
                grantTypes,
                redirectUris,
                Set.of(),
                corsOrigins
        );
    }
}
