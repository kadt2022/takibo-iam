package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.model.ValidatedSets;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientGrantPolicyTest {

    private final OAuthClientGrantPolicy policy =
            new OAuthClientGrantPolicy();

    @Test
    void defaults_pkce_for_a_public_authorization_code_client() {
        OAuthClientRegistration registration = registration(null);
        ValidatedSets sets = sets(Set.of("https://app.example"));

        assertThat(policy.resolvePkce(registration, sets)).isTrue();
    }

    @Test
    void accepts_a_complete_public_spa_profile() {
        OAuthClientRegistration registration = registration(true);
        ValidatedSets sets = sets(Set.of("https://app.example"));

        policy.validateGrantProfile(
                registration,
                TokenEndpointAuthMethod.none,
                sets,
                true
        );
    }

    @Test
    void rejects_a_public_spa_without_a_cors_origin() {
        OAuthClientRegistration registration = registration(true);
        ValidatedSets sets = sets(Set.of());

        assertThatThrownBy(() -> policy.validateGrantProfile(
                registration,
                TokenEndpointAuthMethod.none,
                sets,
                true
        ))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage("PUBLIC clients should declare corsOrigins");
    }

    private static OAuthClientRegistration registration(Boolean requirePkce) {
        return new OAuthClientRegistration(
                "client-test",
                "Client Test",
                ClientType.PUBLIC,
                false,
                TokenEndpointAuthMethod.none,
                requirePkce,
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

    private static ValidatedSets sets(Set<String> corsOrigins) {
        return new ValidatedSets(
                Set.of("authorization_code"),
                Set.of("api:read"),
                Set.of("https://app.example/callback"),
                Set.of(),
                corsOrigins
        );
    }
}
