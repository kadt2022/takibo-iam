package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fige ce que {@code OAuth2AuthorizationService.save()} persiste réellement après une
 * émission {@code client_credentials} réussie (TAS-GRANTS-00, mis à jour par TAS-GRANTS-02).
 * <p>
 * TAS-GRANTS-00 observait ce contrat via un enregistreur déclaré côté test uniquement, faute
 * de bean de production. Ce détour a disparu avec lui : {@code JpaOAuth2AuthorizationService}
 * est désormais le bean réel du contexte, et un second bean de ce type déclaré ici l'aurait
 * rendu ambigu pour Spring Authorization Server. Ce test observe donc directement la ligne
 * {@code oauth2_authorization} laissée en base — la même persistance que verrait un
 * redémarrage, pas un artefact de test.
 * <ul>
 *   <li>{@code registeredClientId} porte l'identifiant technique du client, jamais le
 *       {@code client_id} public ;</li>
 *   <li>le client PLATFORM produit une autorisation sans organisation ni space.</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class AuthorizationSaveContractBaselineIntegrationTest extends TasPostgresBaseline {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OAuth2AuthorizationService authorizationService;

    @Value("${takibo.dev.postman-client.secret}")
    private String platformClientSecret;

    private TasBaselineDataset dataset;

    @BeforeEach
    void seed() {
        dataset = new TasBaselineDataset(jdbc, passwordEncoder);
        dataset.reset();
    }

    @Test
    void given_platform_success_then_one_authorization_is_saved_without_any_tenant() {
        HttpResponse<String> response = requestToken(
                TasBaselineDataset.PLATFORM_CLIENT_ID, platformClientSecret, "api.read");
        assertThat(response.statusCode()).isEqualTo(200);

        Map<String, Object> row = singlePersistedAuthorizationRow();

        assertThat(row.get("principal_name")).isEqualTo(TasBaselineDataset.PLATFORM_CLIENT_ID);
        assertThat(row.get("authorization_grant_type")).isEqualTo("client_credentials");
        assertThat(row.get("authorized_scopes")).isEqualTo("api.read");
        assertThat(row.get("subject_type")).isEqualTo("CLIENT_APP");
        assertThat(row.get("principal_account_id")).isNull();
        // PLATFORM : ni organisation, ni space.
        assertThat(row.get("org_id")).isNull();
        assertThat(row.get("space_id")).isNull();

        // Le client PLATFORM est in-memory : son identifiant technique ne correspond a
        // aucune ligne de oauth2_clients. fk_oauth2_authz_client_scope, qui referencait le
        // client_id public, a ete retiree pour cette raison meme (V202608290001).
        Long matchingClientRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oauth2_clients WHERE id = CAST(? AS uuid)",
                Long.class, row.get("registered_client_id"));
        assertThat(matchingClientRows).isZero();
    }

    @Test
    void given_space_success_then_saved_authorization_uses_the_technical_client_identifier() {
        HttpResponse<String> response = requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE);
        assertThat(response.statusCode()).isEqualTo(200);

        Map<String, Object> row = singlePersistedAuthorizationRow();

        // Identifiant technique, jamais le client_id public.
        assertThat(row.get("registered_client_id"))
                .isEqualTo(TasBaselineDataset.SPACE_CLIENT_UUID.toString());
        assertThat(row.get("principal_name")).isEqualTo(TasBaselineDataset.SPACE_CLIENT_ID);
        assertThat(row.get("authorized_scopes")).isEqualTo(TasBaselineDataset.SPACE_CLIENT_SCOPE);
        assertThat(row.get("org_id")).isEqualTo(TasBaselineDataset.ORG_ID);
        assertThat(row.get("space_id")).isEqualTo(TasBaselineDataset.SPACE_ID);
    }

    @Test
    void given_success_then_saved_access_token_is_encrypted_but_reads_back_as_the_one_returned() {
        HttpResponse<String> response = requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE);
        String returned = accessToken(response);

        Map<String, Object> row = singlePersistedAuthorizationRow();

        // Jamais en clair en base...
        assertThat(row.get("access_token_value")).isNotEqualTo(returned);
        assertThat((String) row.get("access_token_value")).contains("$");
        assertThat(row.get("access_token_hash")).isNotNull();
        assertThat(row.get("access_token_issued_at")).isNotNull();
        assertThat(row.get("access_token_expires_at")).isNotNull();
        // ... mais OAuth2AuthorizationService.findByToken doit retrouver exactement ce qui a
        // ete emis : c'est le contrat que Spring Authorization Server exige pour introspecter
        // ou revoquer ce meme token plus tard.
        OAuth2Authorization reloaded = authorizationService.findByToken(
                returned, OAuth2TokenType.ACCESS_TOKEN);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getToken(OAuth2AccessToken.class).getToken().getTokenValue())
                .isEqualTo(returned);
        // Aucun refresh token en client_credentials : la RFC l'interdit.
        assertThat(row.get("refresh_token_value")).isNull();
    }

    @Test
    void given_refused_request_then_nothing_is_saved() {
        requestToken(TasBaselineDataset.SPACE_CLIENT_ID, "not-the-secret",
                TasBaselineDataset.SPACE_CLIENT_SCOPE);

        assertThat(dataset.countAuthorizationRows()).isZero();
    }

    private Map<String, Object> singlePersistedAuthorizationRow() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM oauth2_authorization");
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    // ---------- Appel HTTP ----------

    private HttpResponse<String> requestToken(String clientId, String secret, String scope) {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        String form = Map.of("grant_type", "client_credentials", "scope", scope)
                .entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/oauth2/token"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel /oauth2/token interrompu", e);
        } catch (Exception e) {
            throw new IllegalStateException("Appel /oauth2/token en echec", e);
        }
    }

    private static String accessToken(HttpResponse<String> response) {
        try {
            return JSON.readTree(response.body()).path("access_token").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Reponse illisible: " + response.body(), e);
        }
    }
}
