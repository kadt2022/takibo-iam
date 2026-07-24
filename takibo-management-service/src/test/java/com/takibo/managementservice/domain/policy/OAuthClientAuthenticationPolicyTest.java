package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.exception.PublicClientMustNotHaveSecretException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientAuthenticationPolicyTest {

    private final OAuthClientAuthenticationPolicy policy =
            new OAuthClientAuthenticationPolicy();

    @Test
    void defaults_public_clients_to_no_authentication_and_no_secret() {
        OAuthClientRegistration registration = registration(
                ClientType.PUBLIC,
                false,
                null
        );

        TokenEndpointAuthMethod method =
                policy.resolveAuthMethod(registration);

        assertThat(method).isEqualTo(TokenEndpointAuthMethod.none);
        assertThat(policy.requiresSecret(registration, method)).isFalse();
    }

    @Test
    void defaults_confidential_clients_to_basic_secret_authentication() {
        OAuthClientRegistration registration = registration(
                ClientType.CONFIDENTIAL,
                true,
                null
        );

        TokenEndpointAuthMethod method =
                policy.resolveAuthMethod(registration);

        assertThat(method)
                .isEqualTo(TokenEndpointAuthMethod.client_secret_basic);
        assertThat(policy.requiresSecret(registration, method)).isTrue();
    }

    @Test
    void rejects_a_secret_on_a_public_client() {
        OAuthClientRegistration registration = registration(
                ClientType.PUBLIC,
                true,
                TokenEndpointAuthMethod.none
        );

        assertThatThrownBy(() ->
                policy.validateAuthenticationProfile(
                        registration,
                        TokenEndpointAuthMethod.none
                )
        ).isInstanceOf(PublicClientMustNotHaveSecretException.class);
    }

    private static OAuthClientRegistration registration(
            ClientType type,
            Boolean requireSecret,
            TokenEndpointAuthMethod method
    ) {
        return new OAuthClientRegistration(
                "client-test",
                "Client Test",
                type,
                requireSecret,
                method,
                true,
                false,
                null,
                null,
                "RS256",
                900,
                3600,
                900,
                null,
                Set.of("api:read"),
                Set.of("authorization_code"),
                Set.of("https://app.example/callback"),
                Set.of(),
                Set.of("https://app.example")
        );
    }
}
