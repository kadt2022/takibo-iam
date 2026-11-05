package com.takibo.managementservice.domain.validation;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Règles de sécurité appliquées au matériel de clé déclaré par un client
 * OAuth (jwksUri / jwksJson), testées sans passer par le validateur de
 * métadonnées pour isoler chaque refus.
 */
class OAuthClientJwkSetValidatorTest {

    private static final String JWKS_URI = "https://keys.example/jwks.json";

    // Clés stockées sous forme Nimbus : un champ typé java.security.interfaces.*
    // serait pris par la règle ArchUnit "..interfaces.." du domaine.
    private static RSAKey rsaPublic;
    private static ECKey p256KeyPair;
    private static ECKey p384Public;
    private static ECKey p521Public;

    private final OAuthClientJwkSetValidator validator =
            new OAuthClientJwkSetValidator();

    @BeforeAll
    static void generateKeys() throws Exception {
        rsaPublic = new RSAKeyGenerator(2048).generate().toPublicJWK();
        p256KeyPair = new ECKeyGenerator(Curve.P_256).generate();
        p384Public = new ECKeyGenerator(Curve.P_384).generate().toPublicJWK();
        p521Public = new ECKeyGenerator(Curve.P_521).generate().toPublicJWK();
    }

    // ---------------------------------------------------------------
    // Règles structurelles (présence / exclusivité / private_key_jwt)
    // ---------------------------------------------------------------

    @Test
    void rejects_a_remote_and_an_inline_key_set_declared_together() {
        assertRejected(
                privateKeyJwt().jwksUri(JWKS_URI).jwksJson(set(rsa().build())),
                "jwksUri and jwksJson are mutually exclusive"
        );
    }

    @Test
    void rejects_both_sources_even_for_a_client_that_does_not_use_private_key_jwt() {
        assertRejected(
                secretClient().jwksUri(JWKS_URI).jwksJson(set(rsa().build())),
                "jwksUri and jwksJson are mutually exclusive"
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void private_key_jwt_requires_a_key_source_and_treats_blank_values_as_absent(
            String blank
    ) {
        assertRejected(
                privateKeyJwt().jwksUri(blank).jwksJson(blank),
                "private_key_jwt requires jwksUri or jwksJson"
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void private_key_jwt_requires_a_signing_algorithm(String algorithm) {
        assertRejected(
                privateKeyJwt().jwksUri(JWKS_URI).idTokenSignedAlg(algorithm),
                "private_key_jwt requires idTokenSignedAlg"
        );
    }

    @Test
    void private_key_jwt_must_not_require_a_client_secret() {
        assertRejected(
                privateKeyJwt().jwksUri(JWKS_URI).requireClientSecret(true),
                "private_key_jwt must not require a client secret"
        );
    }

    @Test
    void private_key_jwt_accepts_an_unspecified_secret_requirement() {
        assertAccepted(
                privateKeyJwt().jwksUri(JWKS_URI).requireClientSecret(null)
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenEndpointAuthMethod.class,
            names = "private_key_jwt",
            mode = EnumSource.Mode.EXCLUDE
    )
    void other_authentication_methods_do_not_need_any_key_material(
            TokenEndpointAuthMethod method
    ) {
        assertAccepted(
                secretClient()
                        .authMethod(method)
                        .idTokenSignedAlg(null)
                        .requireClientSecret(true)
        );
    }

    @Test
    void an_unresolved_authentication_method_does_not_need_any_key_material() {
        assertAccepted(secretClient().authMethod(null));
    }

    // ---------------------------------------------------------------
    // jwksUri
    // ---------------------------------------------------------------

    @Test
    void accepts_an_https_jwks_uri_up_to_255_characters() {
        String prefix = "https://keys.example/";
        String uri = prefix + "k".repeat(255 - prefix.length());

        assertAccepted(privateKeyJwt().jwksUri(uri));
    }

    @Test
    void rejects_a_jwks_uri_longer_than_255_characters() {
        String prefix = "https://keys.example/";
        String uri = prefix + "k".repeat(256 - prefix.length());

        assertRejected(
                privateKeyJwt().jwksUri(uri),
                "jwksUri exceeds 255 characters"
        );
    }

    @Test
    void accepts_an_https_scheme_written_in_upper_case() {
        assertAccepted(privateKeyJwt().jwksUri("HTTPS://keys.example/jwks"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://keys.example/jwks.json",
            "/jwks.json",
            "keys.example/jwks.json",
            "https:///jwks.json",
            "https://user:pass@keys.example/jwks.json",
            "https://keys.example/jwks.json#main",
            "https://keys example/jwks.json"
    })
    void rejects_a_jwks_uri_that_is_not_an_absolute_http_url(String uri) {
        assertRejected(
                privateKeyJwt().jwksUri(uri),
                "jwksUri must be an absolute HTTP(S) URL without user-info or fragment"
        );
    }

    /**
     * Le domaine ne juge que la forme : HTTP n'y est plus refusé, car seule la politique de
     * destination sait si l'installation l'autorise (profil dev, nom de confiance). Le refus
     * par défaut de HTTP est prouvé par OAuthClientServiceTest et, de bout en bout, par
     * JwksUriSsrfPostgresIntegrationTest.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://keys.example/jwks.json",
            "http://localhost:8080/realms/takibo/protocol/openid-connect/certs"
    })
    void leaves_the_transport_decision_to_the_destination_policy(String uri) {
        assertAccepted(privateKeyJwt().jwksUri(uri));
    }

    // ---------------------------------------------------------------
    // jwksJson : forme du document
    // ---------------------------------------------------------------

    @Test
    void rejects_an_inline_key_set_longer_than_32768_characters() {
        assertRejected(
                privateKeyJwt().jwksJson(" ".repeat(32_768) + "x"),
                "jwksJson exceeds 32768 characters"
        );
    }

    @Test
    void parses_an_inline_key_set_of_exactly_32768_characters() {
        String padded = padTo(set(rsa().build()), 32_768);

        assertAccepted(privateKeyJwt().jwksJson(padded));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "[]",
            "{}",
            "{\"keys\":{}}",
            "{\"keys\":[{\"kty\":\"RSA\"}]}"
    })
    void rejects_a_document_that_is_not_a_jwk_set(String json) {
        assertRejected(
                privateKeyJwt().jwksJson(json),
                "jwksJson must be a valid JWK Set"
        );
    }

    @Test
    void a_key_of_unknown_type_is_ignored_by_the_parser_and_leaves_the_set_empty() {
        assertRejected(
                privateKeyJwt().jwksJson("{\"keys\":[{\"kty\":\"unknown\"}]}"),
                "jwksJson must contain between 1 and 10 public keys"
        );
    }

    @Test
    void rejects_an_empty_key_set() {
        assertRejected(
                privateKeyJwt().jwksJson("{\"keys\":[]}"),
                "jwksJson must contain between 1 and 10 public keys"
        );
    }

    @Test
    void accepts_ten_keys_and_rejects_eleven() {
        assertAccepted(privateKeyJwt().jwksJson(rsaKeys(10)));
        assertRejected(
                privateKeyJwt().jwksJson(rsaKeys(11)),
                "jwksJson must contain between 1 and 10 public keys"
        );
    }

    // ---------------------------------------------------------------
    // jwksJson : nature de chaque clé
    // ---------------------------------------------------------------

    @Test
    void rejects_a_symmetric_key() {
        assertRejected(
                privateKeyJwt().jwksJson(
                        "{\"keys\":[{\"kty\":\"oct\",\"k\":\"c2VjcmV0LXNlY3JldA\"}]}"
                ),
                "jwksJson contains an unsupported key type"
        );
    }

    @Test
    void rejects_an_octet_key_pair_even_when_public() {
        // Clé publique Ed25519 de l'exemple RFC 8037, section A.2.
        String ed25519 = "{\"keys\":[{\"kty\":\"OKP\",\"crv\":\"Ed25519\","
                + "\"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}]}";

        assertRejected(
                secretClient().idTokenSignedAlg(null).jwksJson(ed25519),
                "jwksJson contains an unsupported key type"
        );
    }

    @Test
    void rejects_private_ec_key_material() {
        assertRejected(
                privateKeyJwt().idTokenSignedAlg("ES256")
                        .jwksJson(privateKeySet(p256KeyPair)),
                "jwksJson must contain public key material only"
        );
    }

    @Test
    void rejects_a_key_published_for_encryption() {
        assertRejected(
                privateKeyJwt().jwksJson(set(rsa().keyUse(KeyUse.ENCRYPTION).build())),
                "jwksJson keys must be usable for signatures"
        );
    }

    @Test
    void rejects_a_key_with_an_unknown_public_key_use() {
        String json = set(rsa().build())
                .replace("\"kty\":\"RSA\"", "\"kty\":\"RSA\",\"use\":\"custom\"");

        assertRejected(
                privateKeyJwt().jwksJson(json),
                "jwksJson keys must be usable for signatures"
        );
    }

    @Test
    void accepts_a_key_published_for_signatures() {
        assertAccepted(
                privateKeyJwt().jwksJson(set(rsa().keyUse(KeyUse.SIGNATURE).build()))
        );
    }

    @Test
    void rejects_key_operations_that_do_not_include_verify() {
        assertRejected(
                privateKeyJwt().jwksJson(set(
                        rsa().keyOperations(Set.of(KeyOperation.SIGN)).build()
                )),
                "jwksJson keys must allow signature verification"
        );
    }

    @Test
    void accepts_key_operations_that_include_verify() {
        assertAccepted(privateKeyJwt().jwksJson(set(
                rsa().keyOperations(Set.of(KeyOperation.VERIFY)).build()
        )));
    }

    @Test
    void accepts_an_empty_key_operations_list() {
        String json = set(rsa().build())
                .replace("\"kty\":\"RSA\"", "\"kty\":\"RSA\",\"key_ops\":[]");

        assertAccepted(privateKeyJwt().jwksJson(json));
    }

    @Test
    void rejects_an_ec_point_that_is_not_on_the_declared_curve() {
        ECKey valid = p256KeyPair.toPublicJWK();
        String json = set(valid).replace(
                "\"y\":\"" + valid.getY() + "\"",
                "\"y\":\"" + valid.getX() + "\""
        );

        assertRejected(
                privateKeyJwt().idTokenSignedAlg("ES256").jwksJson(json),
                "jwksJson must be a valid JWK Set"
        );
    }

    @Test
    void every_key_is_checked_even_when_a_compatible_key_is_present() {
        assertRejected(
                privateKeyJwt().jwksJson(set(
                        rsa().keyID("good").build(),
                        rsa().keyID("bad").keyUse(KeyUse.ENCRYPTION).build()
                )),
                "jwksJson keys must be usable for signatures"
        );
    }

    // ---------------------------------------------------------------
    // jwksJson : compatibilité avec idTokenSignedAlg
    // ---------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"RS256", "RS384", "RS512", "PS256", "PS384", "PS512"})
    void rsa_keys_are_compatible_with_every_rsa_family_algorithm(String algorithm) {
        assertAccepted(
                privateKeyJwt().idTokenSignedAlg(algorithm)
                        .jwksJson(set(rsa().build()))
        );
    }

    @ParameterizedTest
    @MethodSource("ecCompatibilityMatrix")
    void ec_keys_are_only_compatible_with_the_algorithm_of_their_curve(
            String algorithm,
            Curve curve,
            boolean compatible
    ) {
        Builder registration = privateKeyJwt()
                .idTokenSignedAlg(algorithm)
                .jwksJson(set(ec(curve).build()));

        if (compatible) {
            assertAccepted(registration);
        } else {
            assertRejected(
                    registration,
                    "jwksJson does not contain a key compatible with idTokenSignedAlg"
            );
        }
    }

    static Stream<Arguments> ecCompatibilityMatrix() {
        return Stream.of(
                Arguments.of("ES256", Curve.P_256, true),
                Arguments.of("ES384", Curve.P_384, true),
                Arguments.of("ES512", Curve.P_521, true),
                Arguments.of("ES256", Curve.P_384, false),
                Arguments.of("ES384", Curve.P_521, false),
                Arguments.of("ES512", Curve.P_256, false),
                Arguments.of("RS256", Curve.P_256, false),
                Arguments.of("PS256", Curve.P_256, false)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"ES256", "ES384", "ES512"})
    void an_rsa_key_is_not_compatible_with_an_ec_algorithm(String algorithm) {
        assertRejected(
                privateKeyJwt().idTokenSignedAlg(algorithm)
                        .jwksJson(set(rsa().build())),
                "jwksJson does not contain a key compatible with idTokenSignedAlg"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"HS256", "none", "EdDSA", "rs256"})
    void an_algorithm_outside_the_supported_list_matches_no_key(String algorithm) {
        assertRejected(
                privateKeyJwt().idTokenSignedAlg(algorithm)
                        .jwksJson(set(rsa().build(), ec(Curve.P_256).build())),
                "jwksJson does not contain a key compatible with idTokenSignedAlg"
        );
    }

    @Test
    void a_key_pinned_to_another_algorithm_is_not_compatible() {
        assertRejected(
                privateKeyJwt().idTokenSignedAlg("RS256")
                        .jwksJson(set(rsa().algorithm(JWSAlgorithm.RS384).build())),
                "jwksJson does not contain a key compatible with idTokenSignedAlg"
        );
    }

    @Test
    void a_key_pinned_to_the_configured_algorithm_is_compatible() {
        assertAccepted(
                privateKeyJwt().idTokenSignedAlg("PS384")
                        .jwksJson(set(rsa().algorithm(JWSAlgorithm.PS384).build()))
        );
    }

    @Test
    void one_compatible_key_is_enough_for_a_mixed_key_set() {
        assertAccepted(
                privateKeyJwt().idTokenSignedAlg("ES384")
                        .jwksJson(set(
                                rsa().keyID("rsa").build(),
                                ec(Curve.P_384).keyID("ec").build()
                        ))
        );
    }

    @Test
    void without_a_configured_algorithm_any_valid_signature_key_is_accepted() {
        assertAccepted(
                secretClient().idTokenSignedAlg(null)
                        .jwksJson(set(ec(Curve.P_521).build()))
        );
    }

    @Test
    void a_key_pinned_to_an_encryption_algorithm_is_not_compatible() {
        assertRejected(
                privateKeyJwt().idTokenSignedAlg("RS256")
                        .jwksJson(set(rsa().algorithm(JWEAlgorithm.RSA_OAEP_256).build())),
                "jwksJson does not contain a key compatible with idTokenSignedAlg"
        );
    }

    // ---------------------------------------------------------------
    // Outils
    // ---------------------------------------------------------------

    private void assertAccepted(Builder registration) {
        assertThatCode(() -> validator.validate(registration.build()))
                .doesNotThrowAnyException();
    }

    private void assertRejected(Builder registration, String message) {
        OAuthClientRegistration built = registration.build();
        assertThatThrownBy(() -> validator.validate(built))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage(message);
    }

    private static RSAKey.Builder rsa() {
        return new RSAKey.Builder(rsaPublic);
    }

    private static ECKey.Builder ec(Curve curve) {
        ECKey key;
        if (Curve.P_256.equals(curve)) {
            key = p256KeyPair.toPublicJWK();
        } else if (Curve.P_384.equals(curve)) {
            key = p384Public;
        } else {
            key = p521Public;
        }
        return new ECKey.Builder(key);
    }

    private static String set(JWK... keys) {
        return new JWKSet(List.of(keys)).toString();
    }

    private static String privateKeySet(JWK key) {
        return new JWKSet(key).toString(false);
    }

    private static String rsaKeys(int count) {
        List<JWK> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(rsa().keyID("rsa-" + i).build());
        }
        return new JWKSet(keys).toString();
    }

    private static String padTo(String json, int length) {
        return json + " ".repeat(length - json.length());
    }

    private static Builder privateKeyJwt() {
        return new Builder()
                .authMethod(TokenEndpointAuthMethod.private_key_jwt)
                .requireClientSecret(false)
                .idTokenSignedAlg("RS256");
    }

    private static Builder secretClient() {
        return new Builder()
                .authMethod(TokenEndpointAuthMethod.client_secret_basic)
                .requireClientSecret(true)
                .idTokenSignedAlg("RS256");
    }

    private static final class Builder {
        private TokenEndpointAuthMethod authMethod;
        private Boolean requireClientSecret;
        private String jwksUri;
        private String jwksJson;
        private String idTokenSignedAlg;

        Builder authMethod(TokenEndpointAuthMethod value) {
            authMethod = value;
            return this;
        }

        Builder requireClientSecret(Boolean value) {
            requireClientSecret = value;
            return this;
        }

        Builder jwksUri(String value) {
            jwksUri = value;
            return this;
        }

        Builder jwksJson(String value) {
            jwksJson = value;
            return this;
        }

        Builder idTokenSignedAlg(String value) {
            idTokenSignedAlg = value;
            return this;
        }

        OAuthClientRegistration build() {
            return new OAuthClientRegistration(
                    "jwk-client",
                    "JWK Client",
                    ClientType.CONFIDENTIAL,
                    requireClientSecret,
                    authMethod,
                    false,
                    false,
                    jwksUri,
                    jwksJson,
                    idTokenSignedAlg,
                    900,
                    3600,
                    900,
                    null,
                    Set.of("api:read"),
                    Set.of("authorization_code"),
                    Set.of("https://app.example/callback"),
                    Set.of(),
                    Set.of()
            );
        }
    }
}
