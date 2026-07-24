package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import org.junit.jupiter.api.Test;

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

    private static OAuthClientRegistration registration(
            Set<String> grantTypes,
            Set<String> redirectUris,
            Set<String> corsOrigins
    ) {
        return new OAuthClientRegistration(
                "client-test",
                "Client Test",
                ClientType.PUBLIC,
                false,
                TokenEndpointAuthMethod.none,
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
