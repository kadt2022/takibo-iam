package com.takibo.iamboot.tas;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aucune régression pour une installation locale légitime : sous le profil {@code dev}, un
 * Keycloak lancé en {@code start-dev} sert ses clés en HTTP sur {@code localhost}, et Takibo
 * vérifie réellement une assertion {@code private_key_jwt} contre elles.
 * <p>
 * Chaîne réelle de bout en bout sur PostgreSQL ; seule la configuration du profil dev
 * ({@code application-dev.yml}) ouvre cette destination.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles({"test", "dev"})
@EnabledIf("dockerIsAvailable")
class JwksUriDevelopmentPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000d5");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000d5");
    private static final UUID ADMIN_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000d5");
    private static final UUID ADMIN_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000d5");
    private static final String ORG_CODE = "jwks-dev-org";
    private static final String CLIENT_ID = "local-keycloak-client";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;
    @Value("${takibo.tas.issuer}") private String issuer;

    private TasHttpFixture fixture;
    private RSAKey clientKey;
    private HttpServer keycloakCerts;
    private final AtomicInteger certsRequests = new AtomicInteger();

    @BeforeEach
    void startLocalKeycloakCertsEndpoint() throws Exception {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE);
        fixture.organizationWithSpaceAndAdmin(ORG_ID, ORG_CODE, SPACE_ID, "jwks-dev-space", ADMIN_ACCOUNT, ADMIN_USER);

        clientKey = new RSAKeyGenerator(2048).keyID("local-key").generate();
        byte[] jwks = new JWKSet(clientKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        keycloakCerts = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        keycloakCerts.createContext("/realms/takibo/protocol/openid-connect/certs", exchange -> {
            certsRequests.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(jwks);
            }
        });
        keycloakCerts.start();
    }

    @AfterEach
    void cleanUp() {
        keycloakCerts.stop(0);
        fixture.purgeOrganizations(ORG_CODE);
    }

    @Test
    void given_the_dev_profile_when_keys_are_served_over_http_on_localhost_then_the_assertion_is_verified() throws Exception {
        insertPrivateKeyJwtClient("http://localhost:" + keycloakCerts.getAddress().getPort()
                + "/realms/takibo/protocol/openid-connect/certs");

        HttpResponse<String> response = requestTokenWithAssertion();

        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(200);
        assertThat(TasHttpFixture.body(response).path("access_token").asText()).isNotBlank();
        assertThat(certsRequests).as("clés réellement récupérées").hasValue(1);
    }

    private void insertPrivateKeyJwtClient(String jwksUri) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO oauth2_clients (
                    id, org_id, space_id, client_id, client_name, client_type,
                    require_client_secret, token_endpoint_auth_method, require_pkce,
                    require_consent, jwks_uri, id_token_signed_alg)
                VALUES (?, ?, ?, ?, 'Local Keycloak client', 'CONFIDENTIAL', FALSE, 'private_key_jwt',
                        FALSE, FALSE, ?, 'RS256')
                """, id, ORG_ID, SPACE_ID, CLIENT_ID, jwksUri);
        jdbc.update("""
                INSERT INTO oauth2_client_grant_types (id, client_id, grant_type)
                VALUES (?, ?, 'client_credentials')
                """, UUID.randomUUID(), id);
    }

    private HttpResponse<String> requestTokenWithAssertion() throws Exception {
        Instant now = Instant.now();
        SignedJWT assertion = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(clientKey.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(CLIENT_ID)
                        .subject(CLIENT_ID)
                        .audience(issuer + "/oauth2/token")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(60)))
                        .jwtID(UUID.randomUUID().toString())
                        .build());
        assertion.sign(new RSASSASigner(clientKey));
        String form = "grant_type=client_credentials"
                + "&client_id=" + CLIENT_ID
                + "&client_assertion_type="
                + URLEncoder.encode("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&client_assertion=" + assertion.serialize();
        return fixture.send("POST", "/oauth2/token", null,
                Map.of("Content-Type", "application/x-www-form-urlencoded"), form);
    }
}
