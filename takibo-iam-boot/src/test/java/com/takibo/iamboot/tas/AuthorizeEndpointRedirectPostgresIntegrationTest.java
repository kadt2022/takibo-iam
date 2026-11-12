package com.takibo.iamboot.tas;

import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-TAS-04 — {@code /oauth2/authorize} n'utilise jamais une URI non enregistrée comme
 * destination, y compris pour une réponse d'erreur, et ne répond jamais 500.
 * <p>
 * Requêtes anonymes, comme celles d'un lien de hameçonnage. Le client HTTP du fixture ne suit
 * pas les redirections : une réponse 3xx et son en-tête {@code Location} sont observés tels quels.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class AuthorizeEndpointRedirectPostgresIntegrationTest extends TasPostgresBaseline {

    private static final String ATTACKER = "https://redirect-probe.invalid/phish";
    private static final UUID WEB_CLIENT_UUID = UUID.fromString("dddddddd-0000-0000-0000-0000000000c4");
    private static final String WEB_CLIENT_ID = "sec-tas-04-web-client";
    private static final String WEB_REDIRECT = "https://app.sec-tas-04.test/callback";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;

    private TasHttpFixture fixture;
    private TasBaselineDataset dataset;

    @BeforeEach
    void baselineWithAMachineClientAndAWebClient() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        dataset = new TasBaselineDataset(jdbc, passwordEncoder);
        dataset.reset();
        jdbc.update("""
                INSERT INTO oauth2_clients (
                    id, org_id, space_id, client_id, client_name, client_type,
                    require_client_secret, client_secret_hash, token_endpoint_auth_method,
                    require_pkce, require_consent)
                VALUES (?, ?, ?, ?, 'SEC-TAS-04 web', 'CONFIDENTIAL', TRUE, ?, 'client_secret_basic', TRUE, FALSE)
                """, WEB_CLIENT_UUID, TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID, WEB_CLIENT_ID,
                passwordEncoder.encode("web-secret"));
        jdbc.update("INSERT INTO oauth2_client_grant_types (id, client_id, grant_type) VALUES (?, ?, 'authorization_code')",
                UUID.randomUUID(), WEB_CLIENT_UUID);
        jdbc.update("INSERT INTO oauth2_client_scopes (id, client_id, scope) VALUES (?, ?, 'openid')",
                UUID.randomUUID(), WEB_CLIENT_UUID);
        jdbc.update("INSERT INTO oauth2_client_redirect_uris (id, client_id, uri) VALUES (?, ?, ?)",
                UUID.randomUUID(), WEB_CLIENT_UUID, WEB_REDIRECT);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM oauth2_clients WHERE id = ?", WEB_CLIENT_UUID);
        dataset.clear();
    }

    // ---------- Rouge sur main : redirection ouverte et erreur 500 ----------

    @Test
    void a_machine_client_never_redirects_to_an_attacker_uri() {
        HttpResponse<String> response = authorize(Map.of(
                "response_type", "code", "client_id", TasBaselineDataset.SPACE_CLIENT_ID,
                "redirect_uri", ATTACKER, "state", "s1"));

        assertRefusedOnTheSpot(response, "unauthorized_client");
    }

    @Test
    void a_machine_client_without_redirect_uri_is_refused_without_a_server_error() {
        HttpResponse<String> response = authorize(Map.of(
                "response_type", "code", "client_id", TasBaselineDataset.SPACE_CLIENT_ID, "state", "s2"));

        assertRefusedOnTheSpot(response, "unauthorized_client");
    }

    @Test
    void the_platform_client_never_redirects_to_an_attacker_uri() {
        HttpResponse<String> response = authorize(Map.of(
                "response_type", "code", "client_id", TasBaselineDataset.PLATFORM_CLIENT_ID,
                "redirect_uri", ATTACKER, "state", "s3"));

        assertRefusedOnTheSpot(response, "unauthorized_client");
    }

    // ---------- Contrats conservés ----------

    @Test
    void an_unknown_client_is_refused_on_the_spot() {
        HttpResponse<String> response = authorize(Map.of(
                "response_type", "code", "client_id", "sec-tas-04-unknown", "redirect_uri", ATTACKER));

        assertThat(response.statusCode()).isEqualTo(400);
        assertNeverRedirected(response);
    }

    @Test
    void a_web_client_with_an_unregistered_or_altered_redirect_uri_is_refused_on_the_spot() {
        for (String uri : new String[]{ATTACKER, WEB_REDIRECT + "/../phish", WEB_REDIRECT + "?next=" + ATTACKER,
                "https://app.sec-tas-04.test.redirect-probe.invalid/callback",
                "https://app.sec-tas-04.test/%63allback", "HTTPS://APP.SEC-TAS-04.TEST/callback",
                "https://app.sec-tas-04.test/callback%2F..%2Fphish", "https://app.sec-tas-04.test:443/callback",
                "https://app.sec-tas-04.test@redirect-probe.invalid/callback"}) {
            HttpResponse<String> response = authorize(webRequest(uri));

            assertThat(response.statusCode()).as("redirect_uri=%s : %s", uri, response.body()).isEqualTo(400);
            assertNeverRedirected(response);
        }
    }

    @Test
    void a_web_client_with_its_registered_uri_still_requires_an_authenticated_user() {
        Map<String, String> parameters = webRequest(WEB_REDIRECT);
        parameters.remove("scope");

        HttpResponse<String> response = authorize(parameters);

        assertThat(response.statusCode()).as("utilisateur anonyme : %s %s", response.headers().map(), response.body())
                .isEqualTo(401);
        assertNeverRedirected(response);
    }

    /** RFC 6749 §4.1.2.1 : une fois l'URI validée, l'erreur y est renvoyée — et seulement là. */
    @Test
    void an_error_after_validation_is_sent_to_the_registered_uri_only() {
        HttpResponse<String> response = authorize(webRequest(WEB_REDIRECT));

        assertThat(response.statusCode()).as("scope openid refusé (OIDC désactivé)").isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).get().asString()
                .startsWith(WEB_REDIRECT + "?error=invalid_scope");
    }

    @Test
    void a_hostile_state_cannot_inject_parameters_into_an_error_redirect() {
        String hostile = "s&error=access_denied&redirect_uri=" + ATTACKER;
        Map<String, String> parameters = webRequest(WEB_REDIRECT);
        parameters.put("state", hostile);

        HttpResponse<String> response = authorize(parameters);

        assertThat(response.statusCode()).isEqualTo(302);
        java.net.URI location = java.net.URI.create(response.headers().firstValue("Location").orElseThrow());
        assertThat(location.getHost()).isEqualTo("app.sec-tas-04.test");
        var query = org.springframework.web.util.UriComponentsBuilder.fromUri(location).build(true).getQueryParams();
        assertThat(query.get("error")).containsExactly("invalid_scope");
        assertThat(query).doesNotContainKey("redirect_uri");
        assertThat(java.net.URLDecoder.decode(query.getFirst("state"), StandardCharsets.UTF_8)).isEqualTo(hostile);
    }

    @Test
    void client_credentials_still_issues_a_token_to_the_machine_client() {
        String basic = Base64.getEncoder().encodeToString((TasBaselineDataset.SPACE_CLIENT_ID + ":"
                + TasBaselineDataset.SPACE_CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> response = fixture.send("POST", "/oauth2/token", null,
                Map.of("Authorization", "Basic " + basic, "Content-Type", "application/x-www-form-urlencoded"),
                "grant_type=client_credentials&scope=" + TasBaselineDataset.SPACE_CLIENT_SCOPE);

        assertThat(response.statusCode()).as("réponse : %s", response.body()).isEqualTo(200);
        assertThat(TasHttpFixture.body(response).path("access_token").asText()).isNotBlank();
    }

    // ---------- Outils ----------

    private void assertRefusedOnTheSpot(HttpResponse<String> response, String expectedError) {
        assertThat(response.statusCode()).as("réponse brute : %s %s", response.headers().map(), response.body())
                .isEqualTo(400);
        assertNeverRedirected(response);
        assertThat(TasHttpFixture.body(response).path("error").asText()).isEqualTo(expectedError);
    }

    private static void assertNeverRedirected(HttpResponse<String> response) {
        assertThat(response.statusCode() / 100).as("aucune redirection 3xx").isNotEqualTo(3);
        assertThat(response.headers().firstValue("Location")).as("aucune destination").isEmpty();
        assertThat(response.body()).as("l'URI de l'attaquant n'est jamais reprise").doesNotContain("redirect-probe");
    }

    private static Map<String, String> webRequest(String redirectUri) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("response_type", "code");
        parameters.put("client_id", WEB_CLIENT_ID);
        parameters.put("redirect_uri", redirectUri);
        parameters.put("scope", "openid");
        parameters.put("state", "s4");
        parameters.put("code_challenge", CHALLENGE);
        parameters.put("code_challenge_method", "S256");
        return parameters;
    }

    private HttpResponse<String> authorize(Map<String, String> parameters) {
        String query = parameters.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return fixture.send("GET", "/oauth2/authorize?" + query, null, Map.of(), null);
    }
}
