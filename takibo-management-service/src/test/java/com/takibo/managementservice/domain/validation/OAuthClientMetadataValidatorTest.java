package com.takibo.managementservice.domain.validation;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bornes et refus des métadonnées d'un client OAuth, complémentaires des
 * scénarios déjà couverts par {@code OAuthClientConfigurationValidatorTest}.
 */
class OAuthClientMetadataValidatorTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private final OAuthClientMetadataValidator validator =
            new OAuthClientMetadataValidator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void accepts_identity_values_at_their_maximum_length() {
        assertAccepted(registration().clientId("c".repeat(128)).clientName("n".repeat(160)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-starts-with-dash", ".dot", "with/slash", "é-accent", ""})
    void rejects_a_client_id_that_is_not_url_safe(String clientId) {
        assertRejected(
                registration().clientId(clientId),
                "clientId must contain 1 to 128 URL-safe characters"
        );
    }

    @Test
    void accepts_every_url_safe_character_after_the_first() {
        assertAccepted(registration().clientId("a0._~-Z"));
    }

    @Test
    void rejects_a_client_name_containing_a_tab() {
        assertRejected(
                registration().clientName("Client\tName"),
                "clientName must contain at most 160 characters and no control characters"
        );
    }

    @Test
    void rejects_a_registration_without_any_grant_type_collection() {
        assertRejected(registration().grantTypes(null), "at least one grant type is required");
    }

    @Test
    void accepts_collections_at_their_maximum_size() {
        assertAccepted(registration()
                .scopes(values("scope-", 50))
                .grantTypes(values("grant-", 10))
                .redirectUris(values("https://app.example/cb", 20))
                .postLogoutRedirectUris(values("https://app.example/bye", 20))
                .corsOrigins(values("https://app", 20)));
    }

    @Test
    void rejects_more_than_ten_grant_types() {
        assertRejected(
                registration().grantTypes(values("grant-", 11)),
                "grantTypes exceeds the maximum number of values"
        );
    }

    @Test
    void rejects_more_than_twenty_redirect_or_cors_values() {
        assertRejected(
                registration().redirectUris(values("https://app.example/cb", 21)),
                "redirectUris exceeds the maximum number of values"
        );
        assertRejected(
                registration().postLogoutRedirectUris(values("https://app.example/bye", 21)),
                "postLogoutRedirectUris exceeds the maximum number of values"
        );
        assertRejected(
                registration().corsOrigins(values("https://app", 21)),
                "corsOrigins exceeds the maximum number of values"
        );
    }

    @Test
    void rejects_a_null_entry_in_a_collection() {
        Set<String> withNull = new HashSet<>();
        withNull.add(null);

        assertRejected(registration().corsOrigins(withNull), "corsOrigins contains an invalid value");
    }

    @Test
    void rejects_an_oversized_grant_type_or_post_logout_uri() {
        assertRejected(
                registration().grantTypes(Set.of("g".repeat(65))),
                "grantTypes contains an invalid value"
        );
        assertRejected(
                registration().postLogoutRedirectUris(Set.of("u".repeat(256))),
                "postLogoutRedirectUris contains an invalid value"
        );
    }

    @Test
    void accepts_a_scope_at_its_maximum_length_and_rejects_one_character_more() {
        assertAccepted(registration().scopes(Set.of("s".repeat(128))));
        assertRejected(
                registration().scopes(Set.of("s".repeat(129))),
                "scopes contains an invalid value"
        );
    }

    @Test
    void every_token_lifetime_is_optional() {
        assertAccepted(registration().ttls(null, null, null));
    }

    @Test
    void the_refresh_and_access_lifetimes_are_only_compared_when_both_are_set() {
        assertAccepted(registration().ttls(86_400, null, 60));
        assertAccepted(registration().ttls(null, 60, 60));
    }

    @Test
    void a_refresh_token_shorter_than_the_access_token_is_refused() {
        assertRejected(
                registration().ttls(3600, 1800, 60),
                "refreshTokenTtlSeconds must be greater than accessTokenTtlSeconds"
        );
    }

    @Test
    void secret_expiration_must_be_strictly_in_the_future() {
        assertRejected(
                registration().secretExpiration(NOW.minusSeconds(1)),
                "clientSecretExpiresAt must be in the future"
        );
        assertAccepted(registration().secretExpiration(NOW.plusSeconds(1)));
        assertAccepted(registration().secretExpiration(null));
    }

    @Test
    void validates_a_secret_expiration_on_its_own() {
        assertThatThrownBy(() -> validator.validateSecretExpiration(NOW))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage("clientSecretExpiresAt must be in the future");
        assertThatCode(() -> validator.validateSecretExpiration(null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = TokenEndpointAuthMethod.class, names = {"none", "private_key_jwt"})
    void a_secret_expiration_is_refused_for_a_confidential_client_without_secret(
            TokenEndpointAuthMethod method
    ) {
        assertRejected(
                registration().authMethod(method).secretExpiration(NOW.plusSeconds(60)),
                "clientSecretExpiresAt is only valid for clients using a secret"
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenEndpointAuthMethod.class,
            names = {"client_secret_basic", "client_secret_post", "client_secret_jwt"}
    )
    void a_secret_expiration_is_accepted_for_secret_based_methods(
            TokenEndpointAuthMethod method
    ) {
        assertAccepted(registration().authMethod(method).secretExpiration(NOW.plusSeconds(60)));
    }

    @Test
    void a_secret_expiration_with_an_unresolved_method_is_accepted_for_a_confidential_client() {
        assertAccepted(registration().authMethod(null).secretExpiration(NOW.plusSeconds(60)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HS256", "HS512", "RSA1_5", "rs256", ""})
    void refuses_id_token_algorithms_outside_the_asymmetric_allow_list(String algorithm) {
        assertRejected(registration().idTokenSignedAlg(algorithm), "idTokenSignedAlg is not allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512"
    })
    void accepts_every_asymmetric_id_token_algorithm(String algorithm) {
        assertAccepted(registration().idTokenSignedAlg(algorithm));
    }

    private void assertAccepted(Builder registration) {
        assertThatCode(() -> validator.validateRegistration(registration.build()))
                .doesNotThrowAnyException();
    }

    private void assertRejected(Builder registration, String message) {
        OAuthClientRegistration built = registration.build();
        assertThatThrownBy(() -> validator.validateRegistration(built))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage(message);
    }

    private static Set<String> values(String prefix, int count) {
        Set<String> values = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            values.add(prefix + i);
        }
        return values;
    }

    private static Builder registration() {
        return new Builder();
    }

    private static final class Builder {
        private String clientId = "metadata-client";
        private String clientName = "Metadata Client";
        private TokenEndpointAuthMethod authMethod = TokenEndpointAuthMethod.client_secret_basic;
        private String idTokenSignedAlg = "RS256";
        private Integer accessTtl = 900;
        private Integer refreshTtl = 3600;
        private Integer idTtl = 900;
        private Instant secretExpiration;
        private Set<String> scopes = Set.of("api:read");
        private Set<String> grantTypes = Set.of("authorization_code");
        private Set<String> redirectUris = Set.of("https://app.example/callback");
        private Set<String> postLogoutRedirectUris = Set.of();
        private Set<String> corsOrigins = Set.of();

        Builder clientId(String value) {
            clientId = value;
            return this;
        }

        Builder clientName(String value) {
            clientName = value;
            return this;
        }

        Builder authMethod(TokenEndpointAuthMethod value) {
            authMethod = value;
            return this;
        }

        Builder idTokenSignedAlg(String value) {
            idTokenSignedAlg = value;
            return this;
        }

        Builder ttls(Integer access, Integer refresh, Integer id) {
            accessTtl = access;
            refreshTtl = refresh;
            idTtl = id;
            return this;
        }

        Builder secretExpiration(Instant value) {
            secretExpiration = value;
            return this;
        }

        Builder scopes(Set<String> value) {
            scopes = value;
            return this;
        }

        Builder grantTypes(Set<String> value) {
            grantTypes = value;
            return this;
        }

        Builder redirectUris(Set<String> value) {
            redirectUris = value;
            return this;
        }

        Builder postLogoutRedirectUris(Set<String> value) {
            postLogoutRedirectUris = value;
            return this;
        }

        Builder corsOrigins(Set<String> value) {
            corsOrigins = value;
            return this;
        }

        OAuthClientRegistration build() {
            return new OAuthClientRegistration(
                    clientId,
                    clientName,
                    ClientType.CONFIDENTIAL,
                    true,
                    authMethod,
                    false,
                    false,
                    null,
                    null,
                    idTokenSignedAlg,
                    accessTtl,
                    refreshTtl,
                    idTtl,
                    secretExpiration,
                    scopes,
                    grantTypes,
                    redirectUris,
                    postLogoutRedirectUris,
                    corsOrigins
            );
        }
    }
}
