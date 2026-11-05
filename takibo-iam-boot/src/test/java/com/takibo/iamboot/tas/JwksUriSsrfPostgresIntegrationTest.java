package com.takibo.iamboot.tas;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
 * SSRF par {@code jwksUri} : Takibo ne doit jamais ouvrir de connexion vers une destination
 * interne, ni à l'enregistrement du client, ni quand il récupère les clés pour vérifier une
 * assertion {@code private_key_jwt}.
 * <p>
 * Chaîne réelle de bout en bout sur PostgreSQL. Le « service interne » est une socket locale
 * qui compte les connexions TCP acceptées, avant tout TLS : une connexion comptée prouve
 * qu'une socket a été ouverte vers lui. Les clients des tests de récupération sont insérés en
 * base, comme un client enregistré avant le correctif : la protection doit tenir au moment de
 * la requête, quelle que soit la donnée stockée. Aucune destination n'est déclarée de
 * confiance : c'est la configuration par défaut qui est mesurée.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class JwksUriSsrfPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000c5");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000c5");
    private static final UUID ADMIN_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000c5");
    private static final UUID ADMIN_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000c5");
    private static final String ORG_CODE = "ssrf-org";
    private static final String CLIENTS = "/api/v1/orgs/" + ORG_ID + "/spaces/" + SPACE_ID + "/clients";
    private static final String CLIENT_ID = "ssrf-jwks-client";

    private static TasTlsMaterial tls;
    private static SSLSocketFactory jdkDefaultSocketFactory;

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;

    private TasHttpFixture fixture;
    private TasConnectionProbe internalService;

    @BeforeAll
    static void trustTestCertificateForTheJdkDefaultClient() {
        // Le client HTTP du code vulnérable est celui du JDK : sans cette confiance, il
        // échouerait au TLS avant de suivre la redirection, et le test ne mesurerait rien.
        tls = TasTlsMaterial.selfSigned("dns:localhost,ip:127.0.0.1");
        jdkDefaultSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        HttpsURLConnection.setDefaultSSLSocketFactory(tls.context().getSocketFactory());
    }

    @AfterAll
    static void restoreJdkDefaultClient() {
        HttpsURLConnection.setDefaultSSLSocketFactory(jdkDefaultSocketFactory);
    }

    @BeforeEach
    void seed() throws Exception {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE);
        fixture.organizationWithSpaceAndAdmin(ORG_ID, ORG_CODE, SPACE_ID, "ssrf-space", ADMIN_ACCOUNT, ADMIN_USER);
        internalService = new TasConnectionProbe();
    }

    @AfterEach
    void cleanUp() throws Exception {
        internalService.close();
        fixture.purgeOrganizations(ORG_CODE);
    }

    // ---------- Enregistrement ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/jwks",
            "https://2130706433/jwks",
            "https://127.1/jwks",
            "https://[::1]/jwks",
            "https://[::ffff:127.0.0.1]/jwks",
            "https://169.254.169.254/latest/meta-data/",
            "https://10.0.0.5:8443/jwks",
            "https://192.168.1.10/jwks",
            "https://localhost/jwks",
            "http://keys.example.test/jwks",
            "http://127.0.0.1/jwks"
    })
    void given_an_internal_jwks_uri_when_registering_a_client_then_it_is_refused(String jwksUri) {
        HttpResponse<String> response = fixture.postJson(CLIENTS, adminToken(), registrationBody(jwksUri));

        assertThat(response.statusCode())
                .as("enregistrement de %s, réponse brute : %s", jwksUri, response.body())
                .isEqualTo(400);
        assertThat(response.body()).as("le refus porte sur la destination").contains("jwksUri");
        assertThat(fixture.count("SELECT COUNT(*) FROM oauth2_clients WHERE client_id = ?", CLIENT_ID))
                .isZero();
    }

    @Test
    void given_a_public_https_jwks_uri_when_registering_a_client_then_it_is_accepted() {
        HttpResponse<String> response = fixture.postJson(CLIENTS, adminToken(),
                registrationBody("https://keys.example.test/.well-known/jwks.json"));

        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(201);
        assertThat(fixture.count("SELECT COUNT(*) FROM oauth2_clients WHERE client_id = ?", CLIENT_ID))
                .isEqualTo(1L);
    }

    // ---------- Récupération des clés ----------

    @Test
    void given_a_stored_client_with_an_internal_ip_when_authenticating_then_no_connection_is_opened() throws Exception {
        insertPrivateKeyJwtClient("https://127.0.0.1:" + internalService.port() + "/jwks");

        HttpResponse<String> response = requestTokenWithAssertion();

        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(401);
        assertThat(internalService.acceptedConnections())
                .as("connexions ouvertes vers le service interne")
                .isZero();
    }

    @Test
    void given_a_stored_client_with_a_name_resolving_to_loopback_when_authenticating_then_no_connection_is_opened()
            throws Exception {
        insertPrivateKeyJwtClient("https://localhost:" + internalService.port() + "/jwks");

        HttpResponse<String> response = requestTokenWithAssertion();

        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(401);
        assertThat(internalService.acceptedConnections())
                .as("connexions ouvertes vers le service interne")
                .isZero();
    }

    @Test
    void given_a_jwks_uri_redirecting_to_an_internal_service_when_authenticating_then_the_redirect_is_not_followed()
            throws Exception {
        HttpsServer redirector = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        redirector.setHttpsConfigurator(new HttpsConfigurator(tls.context()));
        String internalUrl = "https://127.0.0.1:" + internalService.port() + "/latest/meta-data/";
        redirector.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Location", internalUrl);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        redirector.start();
        try {
            insertPrivateKeyJwtClient("https://localhost:" + redirector.getAddress().getPort() + "/jwks");

            HttpResponse<String> response = requestTokenWithAssertion();

            assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(401);
            assertThat(internalService.acceptedConnections())
                    .as("connexions ouvertes vers la cible de la redirection")
                    .isZero();
        } finally {
            redirector.stop(0);
        }
    }

    // ---------- Outils ----------

    private String adminToken() {
        return fixture.spaceToken(ORG_ID, SPACE_ID, ADMIN_ACCOUNT, ADMIN_USER,
                List.of(TechnicalPermission.SPACE_CLIENTS_MANAGE.code()));
    }

    // authorization_code : le profil client_credentials impose un secret, incompatible avec
    // private_key_jwt. Le refus mesuré doit venir de jwksUri seul, rien d'autre ne le gêne ici.
    private static String registrationBody(String jwksUri) {
        return """
                {"clientId":"%s","clientName":"SSRF probe","clientType":"CONFIDENTIAL",
                 "requireClientSecret":false,"tokenEndpointAuthMethod":"private_key_jwt",
                 "requirePkce":true,"jwksUri":"%s","idTokenSignedAlg":"RS256",
                 "grantTypes":["authorization_code"],
                 "redirectUris":["https://app.example.test/callback"]}"""
                .formatted(CLIENT_ID, jwksUri);
    }

    private void insertPrivateKeyJwtClient(String jwksUri) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO oauth2_clients (
                    id, org_id, space_id, client_id, client_name, client_type,
                    require_client_secret, token_endpoint_auth_method, require_pkce,
                    require_consent, jwks_uri, id_token_signed_alg)
                VALUES (?, ?, ?, ?, 'SSRF probe', 'CONFIDENTIAL', FALSE, 'private_key_jwt',
                        FALSE, FALSE, ?, 'RS256')
                """, id, ORG_ID, SPACE_ID, CLIENT_ID, jwksUri);
        jdbc.update("""
                INSERT INTO oauth2_client_grant_types (id, client_id, grant_type)
                VALUES (?, ?, 'client_credentials')
                """, UUID.randomUUID(), id);
    }

    private HttpResponse<String> requestTokenWithAssertion() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("ssrf-probe").generate();
        Instant now = Instant.now();
        SignedJWT assertion = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                new JWTClaimsSet.Builder()
                        .issuer(CLIENT_ID)
                        .subject(CLIENT_ID)
                        .audience("http://localhost:" + port + "/oauth2/token")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(60)))
                        .jwtID(UUID.randomUUID().toString())
                        .build());
        assertion.sign(new RSASSASigner(key));
        String form = "grant_type=client_credentials"
                + "&client_id=" + CLIENT_ID
                + "&client_assertion_type="
                + URLEncoder.encode("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&client_assertion=" + assertion.serialize();
        return fixture.send("POST", "/oauth2/token", null,
                Map.of("Content-Type", "application/x-www-form-urlencoded"), form);
    }
}
