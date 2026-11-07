package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un client OAuth n'est enregistré qu'avec des métadonnées que le serveur d'autorisation
 * sait utiliser, et une clé trop faible n'authentifie aucun client, même enregistrée
 * avant la règle.
 * <p>
 * Chaîne réelle de bout en bout sur PostgreSQL : enregistrement par l'API, puis
 * authentification {@code private_key_jwt} sur {@code /oauth2/token}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class OAuthClientMetadataConformancePostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000f6");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000f6");
    private static final UUID ADMIN_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000f6");
    private static final UUID ADMIN_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000f6");
    private static final String ORG_CODE = "conformance-org";
    private static final String CLIENTS = "/api/v1/orgs/" + ORG_ID + "/spaces/" + SPACE_ID + "/clients";
    private static final String CLIENT_ID = "conformance-client";
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;
    @Value("${takibo.tas.issuer}") private String issuer;

    private TasHttpFixture fixture;

    @BeforeEach
    void seed() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE);
        fixture.organizationWithSpaceAndAdmin(ORG_ID, ORG_CODE, SPACE_ID, "conformance-space", ADMIN_ACCOUNT, ADMIN_USER);
    }

    @AfterEach
    void cleanUp() {
        fixture.purgeOrganizations(ORG_CODE);
    }

    // ---------- Scopes ----------

    @ParameterizedTest
    @ValueSource(strings = {"api read", "api\"read", "api\\read", "api\tread", "café"})
    void given_a_scope_outside_the_rfc_6749_charset_when_registering_a_client_then_it_is_refused(String scope)
            throws Exception {
        HttpResponse<String> response = register(secretClientBody(List.of("api:read", scope)));

        assertThat(response.statusCode()).as("scope %s, réponse brute : %s", scope, response.body()).isEqualTo(400);
        assertNoClientStored();
    }

    @Test
    void given_rfc_6749_scopes_when_registering_a_client_then_it_is_accepted() throws Exception {
        HttpResponse<String> response = register(secretClientBody(List.of("api:read", "orders.write")));

        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(201);
        assertThat(fixture.count("SELECT COUNT(*) FROM oauth2_clients WHERE client_id = ?", CLIENT_ID))
                .isEqualTo(1L);
    }

    // ---------- Clés intégrées ----------

    @Test
    void given_a_key_pinned_to_an_algorithm_it_cannot_verify_when_registering_then_it_is_refused() throws Exception {
        RSAKey good = new RSAKeyGenerator(2048).keyID("good").generate().toPublicJWK();
        RSAKey incoherent = new RSAKey.Builder(new RSAKeyGenerator(2048).generate().toPublicJWK())
                .keyID("hmac").algorithm(JWSAlgorithm.HS256).build();

        HttpResponse<String> response = register(privateKeyJwtBody(new JWKSet(List.of(good, incoherent)).toString()));

        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(400);
        assertNoClientStored();
    }

    @Test
    void given_a_stored_embedded_key_shorter_than_2048_bits_when_authenticating_then_the_assertion_is_refused()
            throws Exception {
        RSAKey weak = new RSAKeyGenerator(1024, true).keyID("weak-key").generate();
        insertEmbeddedKeyClient(new JWKSet(weak.toPublicJWK()).toString());

        HttpResponse<String> response = requestTokenWithAssertion(weak);

        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(401);
    }

    // ---------- Outils ----------

    private HttpResponse<String> register(Map<String, Object> body) throws Exception {
        String adminToken = fixture.spaceToken(ORG_ID, SPACE_ID, ADMIN_ACCOUNT, ADMIN_USER,
                List.of(TechnicalPermission.SPACE_CLIENTS_MANAGE.code()));
        return fixture.postJson(CLIENTS, adminToken, JSON.writeValueAsString(body));
    }

    private void assertNoClientStored() {
        assertThat(fixture.count("SELECT COUNT(*) FROM oauth2_clients WHERE client_id = ?", CLIENT_ID))
                .as("aucun client enregistré")
                .isZero();
    }

    private static Map<String, Object> secretClientBody(List<String> scopes) {
        return Map.of(
                "clientId", CLIENT_ID,
                "clientName", "Conformance probe",
                "clientType", "CONFIDENTIAL",
                "tokenEndpointAuthMethod", "client_secret_basic",
                "requirePkce", true,
                "grantTypes", List.of("authorization_code"),
                "redirectUris", List.of("https://app.example.test/callback"),
                "scopes", scopes);
    }

    // authorization_code : le profil client_credentials impose un secret, incompatible avec
    // private_key_jwt. Le refus mesuré doit venir du jeu de clés seul.
    private static Map<String, Object> privateKeyJwtBody(String jwksJson) {
        return Map.of(
                "clientId", CLIENT_ID,
                "clientName", "Conformance probe",
                "clientType", "CONFIDENTIAL",
                "requireClientSecret", false,
                "tokenEndpointAuthMethod", "private_key_jwt",
                "requirePkce", true,
                "jwksJson", jwksJson,
                "idTokenSignedAlg", "RS256",
                "grantTypes", List.of("authorization_code"),
                "redirectUris", List.of("https://app.example.test/callback"));
    }

    /** Forme d'un client enregistré avant la règle de taille : la donnée est déjà en base. */
    private void insertEmbeddedKeyClient(String jwksJson) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO oauth2_clients (
                    id, org_id, space_id, client_id, client_name, client_type,
                    require_client_secret, token_endpoint_auth_method, require_pkce,
                    require_consent, jwks_json, id_token_signed_alg)
                VALUES (?, ?, ?, ?, 'Conformance probe', 'CONFIDENTIAL', FALSE, 'private_key_jwt',
                        FALSE, FALSE, ?, 'RS256')
                """, id, ORG_ID, SPACE_ID, CLIENT_ID, jwksJson);
        jdbc.update("""
                INSERT INTO oauth2_client_grant_types (id, client_id, grant_type)
                VALUES (?, ?, 'client_credentials')
                """, UUID.randomUUID(), id);
    }

    private HttpResponse<String> requestTokenWithAssertion(RSAKey key) throws Exception {
        Instant now = Instant.now();
        SignedJWT assertion = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(CLIENT_ID)
                        .subject(CLIENT_ID)
                        .audience(issuer + "/oauth2/token")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(60)))
                        .jwtID(UUID.randomUUID().toString())
                        .build());
        assertion.sign(new RSASSASigner(key.toRSAPrivateKey(), true));
        String form = "grant_type=client_credentials"
                + "&client_id=" + CLIENT_ID
                + "&client_assertion_type="
                + URLEncoder.encode("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&client_assertion=" + assertion.serialize();
        return fixture.send("POST", "/oauth2/token", null,
                Map.of("Content-Type", "application/x-www-form-urlencoded"), form);
    }
}
