package com.takibo.managementservice.application.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.managementservice.application.command.RegisterClientCommand;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientConfigurationValidatorTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private OAuthClientConfigurationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OAuthClientConfigurationValidator(
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void accepts_bounded_secret_client_configuration() {
        assertThatCode(() -> validator.validateRegistration(command().build()))
                .doesNotThrowAnyException();
    }

    @Test
    void accepts_optional_values_and_ttl_upper_bounds() {
        assertThatCode(() -> validator.validateRegistration(
                command()
                        .withIdTokenSignedAlg(null)
                        .withAccessTokenTtl(86_400)
                        .withRefreshTokenTtl(31_536_000)
                        .withIdTokenTtl(86_400)
                        .withScopes(null)
                        .build()
        )).doesNotThrowAnyException();
    }

    @Test
    void rejects_missing_grant_types() {
        assertInvalid(command().withGrantTypes(Set.of()).build(), "at least one grant type");
    }

    @Test
    void rejects_unsafe_identity_values() {
        assertInvalid(command().withClientId("client id").build(), "clientId");
        assertInvalid(command().withClientId("c".repeat(129)).build(), "clientId");
        assertInvalid(command().withClientName("Client\nName").build(), "clientName");
        assertInvalid(command().withClientName("c".repeat(161)).build(), "clientName");
    }

    @Test
    void rejects_oversized_or_blank_collection_values() {
        Set<String> tooManyScopes = new LinkedHashSet<>();
        for (int i = 0; i < 51; i++) {
            tooManyScopes.add("scope-" + i);
        }

        assertInvalid(command().withScopes(tooManyScopes).build(), "scopes exceeds");
        assertInvalid(command().withScopes(Set.of(" ")).build(), "scopes contains");
        assertInvalid(command().withRedirectUris(Set.of("x".repeat(256))).build(), "redirectUris contains");
    }

    @Test
    void rejects_non_positive_or_incoherent_token_ttls() {
        assertInvalid(command().withAccessTokenTtl(0).build(), "accessTokenTtlSeconds must be positive");
        assertInvalid(command().withRefreshTokenTtl(-1).build(), "refreshTokenTtlSeconds must be positive");
        assertInvalid(command().withIdTokenTtl(0).build(), "idTokenTtlSeconds must be positive");
        assertInvalid(command().withAccessTokenTtl(86_401).build(), "accessTokenTtlSeconds exceeds");
        assertInvalid(command().withRefreshTokenTtl(31_536_001).build(), "refreshTokenTtlSeconds exceeds");
        assertInvalid(command().withIdTokenTtl(86_401).build(), "idTokenTtlSeconds exceeds");
        assertInvalid(
                command().withAccessTokenTtl(3600).withRefreshTokenTtl(3600).build(),
                "refreshTokenTtlSeconds must be greater"
        );
    }

    @Test
    void rejects_unsafe_signing_algorithm_and_secret_expiration() {
        assertInvalid(command().withIdTokenSignedAlg("none").build(), "idTokenSignedAlg");
        assertInvalid(command().withSecretExpiration(NOW).build(), "must be in the future");
        assertInvalid(
                command()
                        .withClientType(ClientType.PUBLIC)
                        .withTokenEndpointAuthMethod(TokenEndpointAuthMethod.none)
                        .withRequireClientSecret(false)
                        .build(),
                "only valid for clients using a secret"
        );
    }

    @Test
    void rejects_ambiguous_or_incomplete_private_key_authentication() {
        assertInvalid(
                command().withJwksUri("https://keys.example/jwks.json")
                        .withJwksJson(publicJwkSet()).build(),
                "mutually exclusive"
        );
        assertInvalid(
                command().asPrivateKeyJwt().withJwksJson(null).build(),
                "private_key_jwt requires"
        );
        assertInvalid(
                command().asPrivateKeyJwt().withRequireClientSecret(true).build(),
                "must not require a client secret"
        );
    }

    @Test
    void validates_remote_jwks_endpoint() {
        assertInvalid(command().withJwksUri("http://keys.example/jwks.json").build(), "absolute HTTPS");
        assertInvalid(command().withJwksUri("https://user@keys.example/jwks.json").build(), "absolute HTTPS");
        assertInvalid(command().withJwksUri("https://keys.example/jwks.json#key").build(), "absolute HTTPS");
        assertInvalid(command().withJwksUri("https://keys.example/" + "x".repeat(240)).build(), "exceeds 255");

        assertThatCode(() -> validator.validateRegistration(
                command().withJwksUri("https://keys.example/jwks.json").build()
        )).doesNotThrowAnyException();
    }

    @Test
    void accepts_public_jwk_set_and_rejects_private_or_malformed_material() {
        assertThatCode(() -> validator.validateRegistration(
                command().asPrivateKeyJwt().withJwksJson(publicJwkSet()).build()
        )).doesNotThrowAnyException();

        assertInvalid(command().withJwksJson("not-json").build(), "valid JWK Set");
        assertInvalid(command().withJwksJson("{\"keys\":[]}").build(), "between 1 and 10");
        assertInvalid(
                command().withJwksJson("{\"keys\":[{\"kty\":\"oct\",\"k\":\"secret\"}]}").build(),
                "unsupported key type"
        );
        assertInvalid(
                command().withJwksJson("{\"keys\":[{\"kty\":\"RSA\",\"n\":\"n\",\"e\":\"AQAB\",\"d\":\"private\"}]}").build(),
                "public key material only"
        );
        assertInvalid(
                command().withJwksJson("{\"keys\":[{\"kty\":\"RSA\",\"n\":\"n\"}]}").build(),
                "incomplete public key"
        );
        assertInvalid(
                command().withJwksJson("{\"keys\":[{\"kty\":\"RSA\",\"n\":\"n\",\"e\":1}]}").build(),
                "incomplete public key"
        );
        assertInvalid(command().withJwksJson("x".repeat(32_769)).build(), "exceeds 32768");
    }

    @Test
    void accepts_complete_ec_and_okp_public_keys() {
        assertThatCode(() -> validator.validateRegistration(
                command().withJwksJson(
                        "{\"keys\":["
                                + "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"x\",\"y\":\"y\"},"
                                + "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"x\"}]}"
                ).build()
        )).doesNotThrowAnyException();
    }

    private void assertInvalid(RegisterClientCommand command, String message) {
        assertThatThrownBy(() -> validator.validateRegistration(command))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessageContaining(message);
    }

    private static String publicJwkSet() {
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"key-1\",\"n\":\"n\",\"e\":\"AQAB\"}]}";
    }

    private static CommandBuilder command() {
        return new CommandBuilder();
    }

    private static final class CommandBuilder {
        private String clientId = "secure-client";
        private String clientName = "Secure Client";
        private ClientType clientType = ClientType.CONFIDENTIAL;
        private Boolean requireClientSecret = true;
        private TokenEndpointAuthMethod authMethod = TokenEndpointAuthMethod.client_secret_basic;
        private String jwksUri;
        private String jwksJson;
        private String idTokenSignedAlg = "RS256";
        private Integer accessTokenTtl = 900;
        private Integer refreshTokenTtl = 3600;
        private Integer idTokenTtl = 900;
        private Instant secretExpiration = NOW.plusSeconds(3600);
        private Set<String> scopes = Set.of("api:read");
        private Set<String> grantTypes = Set.of("client_credentials");
        private Set<String> redirectUris = Set.of();

        CommandBuilder withClientId(String value) {
            clientId = value;
            return this;
        }

        CommandBuilder withClientName(String value) {
            clientName = value;
            return this;
        }

        CommandBuilder withClientType(ClientType value) {
            clientType = value;
            return this;
        }

        CommandBuilder withRequireClientSecret(Boolean value) {
            requireClientSecret = value;
            return this;
        }

        CommandBuilder withTokenEndpointAuthMethod(TokenEndpointAuthMethod value) {
            authMethod = value;
            return this;
        }

        CommandBuilder withJwksUri(String value) {
            jwksUri = value;
            return this;
        }

        CommandBuilder withJwksJson(String value) {
            jwksJson = value;
            return this;
        }

        CommandBuilder withIdTokenSignedAlg(String value) {
            idTokenSignedAlg = value;
            return this;
        }

        CommandBuilder withAccessTokenTtl(Integer value) {
            accessTokenTtl = value;
            return this;
        }

        CommandBuilder withRefreshTokenTtl(Integer value) {
            refreshTokenTtl = value;
            return this;
        }

        CommandBuilder withIdTokenTtl(Integer value) {
            idTokenTtl = value;
            return this;
        }

        CommandBuilder withSecretExpiration(Instant value) {
            secretExpiration = value;
            return this;
        }

        CommandBuilder withScopes(Set<String> value) {
            scopes = value;
            return this;
        }

        CommandBuilder withGrantTypes(Set<String> value) {
            grantTypes = value;
            return this;
        }

        CommandBuilder withRedirectUris(Set<String> value) {
            redirectUris = value;
            return this;
        }

        CommandBuilder asPrivateKeyJwt() {
            clientType = ClientType.CONFIDENTIAL;
            requireClientSecret = false;
            authMethod = TokenEndpointAuthMethod.private_key_jwt;
            secretExpiration = null;
            grantTypes = Set.of("authorization_code");
            redirectUris = Set.of("https://app.example/callback");
            jwksJson = publicJwkSet();
            return this;
        }

        RegisterClientCommand build() {
            return new RegisterClientCommand(
                    clientId,
                    clientName,
                    clientType,
                    requireClientSecret,
                    authMethod,
                    false,
                    false,
                    jwksUri,
                    jwksJson,
                    idTokenSignedAlg,
                    accessTokenTtl,
                    refreshTokenTtl,
                    idTokenTtl,
                    secretExpiration,
                    scopes,
                    grantTypes,
                    redirectUris,
                    Set.of(),
                    Set.of()
            );
        }
    }
}
