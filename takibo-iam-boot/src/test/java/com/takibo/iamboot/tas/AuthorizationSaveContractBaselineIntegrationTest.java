package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
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
 * <p>
 * Porte aussi le parcours HTTP minimal émission → introspection active → révocation →
 * introspection inactive. {@code /oauth2/introspect} et {@code /oauth2/revoke} sont les
 * endpoints par défaut de {@code OAuth2AuthorizationServerConfigurer}, non désactivés ici ; ils
 * ne fonctionnent qu'en s'appuyant sur {@code findByToken}/{@code remove} de
 * {@code JpaOAuth2AuthorizationService} — exactement le contrat qu'un {@code save()} devenu
 * no-op pour {@code client_credentials} aurait silencieusement cassé. La révocation avancée
 * (famille de refresh tokens, époque de sécurité) reste hors périmètre, portée par
 * TAS-GRANTS-07.
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

        assertThat(row)
                .containsEntry("principal_name", TasBaselineDataset.PLATFORM_CLIENT_ID)
                .containsEntry("authorization_grant_type", "client_credentials")
                .containsEntry("authorized_scopes", "api.read")
                .containsEntry("subject_type", "CLIENT_APP");
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
        assertThat(row)
                .containsEntry("registered_client_id", TasBaselineDataset.SPACE_CLIENT_UUID.toString())
                .containsEntry("principal_name", TasBaselineDataset.SPACE_CLIENT_ID)
                .containsEntry("authorized_scopes", TasBaselineDataset.SPACE_CLIENT_SCOPE)
                .containsEntry("org_id", TasBaselineDataset.ORG_ID)
                .containsEntry("space_id", TasBaselineDataset.SPACE_ID);
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
        assertThat(row).doesNotContainEntry("access_token_value", returned);
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

    @Test
    void given_a_client_credentials_token_when_introspected_then_revoked_then_introspection_reports_inactive() {
        // Justifie precisement le contrat que le pivot client_credentials aurait casse : sans
        // ligne persistee, /oauth2/introspect et /oauth2/revoke n'auraient plus rien a trouver.
        String token = accessToken(requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE));

        HttpResponse<String> beforeRevocation = introspect(
                TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET, token);
        assertThat(beforeRevocation.statusCode()).isEqualTo(200);
        assertThat(isActive(beforeRevocation)).isTrue();

        HttpResponse<String> revocation = revoke(
                TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET, token);
        assertThat(revocation.statusCode()).isEqualTo(200);

        HttpResponse<String> afterRevocation = introspect(
                TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET, token);
        assertThat(afterRevocation.statusCode()).isEqualTo(200);
        assertThat(isActive(afterRevocation)).isFalse();
    }

    @Test
    void given_an_active_token_when_introspected_then_the_response_identifies_the_client_and_scope() {
        String token = accessToken(requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE));

        HttpResponse<String> introspection = introspect(
                TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET, token);

        assertThat(field(introspection, "client_id")).isEqualTo(TasBaselineDataset.SPACE_CLIENT_ID);
        assertThat(field(introspection, "scope")).contains(TasBaselineDataset.SPACE_CLIENT_SCOPE);
    }

    @Test
    void given_an_unknown_token_when_introspected_then_it_reports_inactive_without_an_error() {
        // RFC 7662 : un jeton inconnu ne se distingue pas d'un jeton revoque -- meme statut,
        // meme corps. Repondre 4xx ici donnerait un oracle d'existence.
        HttpResponse<String> introspection = introspect(
                TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET,
                "un-jeton-qui-n-a-jamais-existe");

        assertThat(introspection.statusCode()).isEqualTo(200);
        assertThat(isActive(introspection)).isFalse();
    }

    @Test
    void given_an_inactive_token_when_introspected_then_the_response_discloses_nothing_else() {
        HttpResponse<String> introspection = introspect(
                TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET,
                "un-jeton-qui-n-a-jamais-existe");

        assertThat(field(introspection, "client_id")).isNull();
        assertThat(field(introspection, "scope")).isNull();
    }

    @Test
    void given_no_client_authentication_when_introspecting_then_it_is_refused_without_disclosure() {
        // L'introspection revele l'etat d'un jeton : sans authentification du client,
        // n'importe qui sonderait n'importe quel jeton.
        //
        // L'assertion porte volontairement sur la propriete de securite (refus + aucune
        // divulgation) plutot que sur un code precis : TenantResolutionFilter repond
        // aujourd'hui invalid_request/400 sur ce point de terminaison, alors que son propre
        // javadoc annonce invalid_client/401 pour /oauth2/introspect et /oauth2/revoke, qui
        // authentifient un client comme /oauth2/token. Cette divergence vient de
        // TAS-GRANTS-01 (deja fusionne) et attend un arbitrage ; ce test reste vrai dans les
        // deux cas plutot que de figer celui qui sera peut-etre corrige.
        String token = accessToken(requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE));

        HttpResponse<String> introspection = postFormWithoutClientAuthentication(
                "/oauth2/introspect", Map.of("token", token));

        assertThat(introspection.statusCode()).isBetween(400, 499);
        assertThat(field(introspection, "active"))
                .as("un refus ne doit rien dire de l'etat du jeton sonde")
                .isNull();
    }

    @Test
    void given_two_tokens_when_one_is_revoked_then_the_other_stays_active() {
        // La revocation porte sur un jeton, jamais sur le client : un second jeton du meme
        // client doit survivre.
        String revoked = accessToken(requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE));
        String kept = accessToken(requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE));

        revoke(TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET, revoked);

        assertThat(isActive(introspect(
                TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET, revoked)))
                .isFalse();
        assertThat(isActive(introspect(
                TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET, kept)))
                .isTrue();
    }

    private Map<String, Object> singlePersistedAuthorizationRow() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM oauth2_authorization");
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    // ---------- Appel HTTP ----------

    private HttpResponse<String> requestToken(String clientId, String secret, String scope) {
        return postForm("/oauth2/token", clientId, secret,
                Map.of("grant_type", "client_credentials", "scope", scope));
    }

    private HttpResponse<String> introspect(String clientId, String secret, String token) {
        return postForm("/oauth2/introspect", clientId, secret, Map.of("token", token));
    }

    private HttpResponse<String> revoke(String clientId, String secret, String token) {
        return postForm("/oauth2/revoke", clientId, secret, Map.of("token", token));
    }

    private HttpResponse<String> postForm(
            String path, String clientId, String secret, Map<String, String> form) {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                .build(), path);
    }

    private HttpResponse<String> postFormWithoutClientAuthentication(
            String path, Map<String, String> form) {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                .build(), path);
    }

    private static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static HttpResponse<String> send(HttpRequest request, String path) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel " + path + " interrompu", e);
        } catch (Exception e) {
            throw new IllegalStateException("Appel " + path + " en echec", e);
        }
    }

    private static String accessToken(HttpResponse<String> response) {
        try {
            return JSON.readTree(response.body()).path("access_token").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Reponse illisible: " + response.body(), e);
        }
    }

    private static boolean isActive(HttpResponse<String> introspectionResponse) {
        try {
            return JSON.readTree(introspectionResponse.body()).path("active").asBoolean();
        } catch (Exception e) {
            throw new IllegalStateException("Reponse illisible: " + introspectionResponse.body(), e);
        }
    }

    /** {@code null} quand le champ est absent — ce que RFC 7662 impose pour un jeton inactif. */
    private static String field(HttpResponse<String> response, String name) {
        try {
            JsonNode node = JSON.readTree(response.body()).get(name);
            return node == null || node.isNull() ? null : node.asText();
        } catch (Exception e) {
            throw new IllegalStateException("Reponse illisible: " + response.body(), e);
        }
    }
}
