package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filet de securite du flux {@code client_credentials} (TAS-GRANTS-00).
 * <p>
 * Reference figee avant l'introduction des nouveaux grant types. Les recits 01 a 07
 * remplaceront la resolution du tenant, la persistance et les clefs de signature ; aucun ne
 * doit changer ce que ce test observe. Une regression ici fait echouer la CI.
 * <p>
 * Deux clients, deux plans :
 * <ul>
 *   <li><b>PLATFORM</b> — {@code postman-client}, declare in-memory, sans organisation ni
 *       space. Son token ne doit porter aucun tenant : c'est ce qui le ferme aux routes
 *       situees.</li>
 *   <li><b>SPACE</b> — client en base, dont le tenant reel est transcrit dans les claims.</li>
 * </ul>
 * Deux choix de methode, tous deux necessaires pour que le filet couvre le chemin reel :
 * <ul>
 *   <li><b>HTTP sur un port reel</b> plutot que MockMvc : {@code TenantResolutionFilter} et
 *       {@code PkceEnforcementFilter} sont des filtres de servlet, hors chaine Spring
 *       Security. Sous MockMvc ils ne s'executeraient pas.</li>
 *   <li><b>Client HTTP du JDK</b> plutot que {@code RestTemplate} : ce dernier perd le corps
 *       des reponses d'erreur, ce qui masquerait precisement les refus a figer.</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                // BCrypt force 4 : le jeu de donnees encode plusieurs empreintes par test.
                // La force de production n'est pas ce que ce filet mesure.
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class ClientCredentialsBaselineIntegrationTest extends TasPostgresBaseline {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private ApplicationContext applicationContext;

    @Value("${takibo.dev.postman-client.secret}")
    private String platformClientSecret;

    private TasBaselineDataset dataset;

    @BeforeEach
    void seed() {
        dataset = new TasBaselineDataset(jdbc, passwordEncoder);
        dataset.reset();
    }

    // ---------- Emission : plan PLATFORM ----------

    @Test
    void given_platform_client_when_token_requested_then_signed_jwt_carries_no_tenant() {
        HttpResponse<String> response = requestToken(
                TasBaselineDataset.PLATFORM_CLIENT_ID, platformClientSecret, "api.read");

        assertThat(response.statusCode()).isEqualTo(200);
        Jwt jwt = jwtDecoder.decode(accessToken(response));

        assertThat(jwt.getClaimAsString("takibo_scope_level")).isEqualTo("PLATFORM");
        assertThat(jwt.getClaimAsString("takibo_tenant_source")).isEqualTo("platform_client");
        assertThat(jwt.getClaimAsString("subject_type")).isEqualTo("CLIENT_APP");
        assertThat(jwt.getClaimAsString("auth_method")).isEqualTo("OAUTH2_CLIENT_CREDENTIALS");

        // Aucun tenant fabrique : un token PLATFORM est ferme aux routes situees.
        assertThat(jwt.hasClaim("org_id")).isFalse();
        assertThat(jwt.hasClaim("space_id")).isFalse();
    }

    // ---------- Emission : plan SPACE ----------

    @Test
    void given_space_client_when_token_requested_then_signed_jwt_carries_its_real_tenant() {
        HttpResponse<String> response = requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE);

        assertThat(response.statusCode()).isEqualTo(200);
        Jwt jwt = jwtDecoder.decode(accessToken(response));

        assertThat(jwt.getClaimAsString("takibo_scope_level")).isEqualTo("SPACE");
        assertThat(jwt.getClaimAsString("takibo_tenant_source")).isEqualTo("oauth2_client");
        assertThat(jwt.getClaimAsString("org_id")).isEqualTo(TasBaselineDataset.ORG_ID.toString());
        assertThat(jwt.getClaimAsString("space_id")).isEqualTo(TasBaselineDataset.SPACE_ID.toString());
        assertThat(jwt.getClaimAsString("subject_type")).isEqualTo("CLIENT_APP");
        assertThat(jwt.getClaimAsString("auth_method")).isEqualTo("OAUTH2_CLIENT_CREDENTIALS");
    }

    @Test
    void given_space_client_when_token_requested_then_subject_is_the_client_not_an_account() {
        HttpResponse<String> response = requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE);

        Jwt jwt = jwtDecoder.decode(accessToken(response));

        assertThat(jwt.getSubject()).isEqualTo(TasBaselineDataset.SPACE_CLIENT_ID);
        // Un token machine ne porte aucun pouvoir humain.
        assertThat(jwt.hasClaim("account_id")).isFalse();
        assertThat(jwt.hasClaim("user_id")).isFalse();
        assertThat(jwt.hasClaim("permissions")).isFalse();
    }

    // ---------- Etat actuel de la persistance : point de bascule vers TAS-GRANTS-02 ----------

    @Test
    void given_current_wiring_then_no_authorization_service_bean_is_declared() {
        // Constat plus precis que "le stockage est en memoire" : l'application ne declare
        // aucun bean. Spring Authorization Server fabrique lui-meme un
        // InMemoryOAuth2AuthorizationService et le garde comme objet partage de son
        // configurer, hors du contexte applicatif.
        // Le recit 02 ne remplacera donc pas une implementation : il introduira le bean qui
        // manque. La chute de ce test est le signal attendu, pas une regression.
        assertThat(applicationContext.getBeanNamesForType(OAuth2AuthorizationService.class))
                .isEmpty();
        assertThat(applicationContext.getBeanNamesForType(OAuth2AuthorizationConsentService.class))
                .isEmpty();
    }

    @Test
    void given_successful_token_when_database_inspected_then_nothing_is_persisted() {
        // La table existe (Flyway l'a creee) mais rien ne l'alimente : aucune autorisation ne
        // survit a un redemarrage, ni n'est partagee entre instances.
        requestToken(TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE);
        requestToken(TasBaselineDataset.PLATFORM_CLIENT_ID, platformClientSecret, "api.read");

        assertThat(dataset.countAuthorizationRows()).isZero();
    }

    // ---------- Refus ----------

    @Test
    void given_wrong_secret_when_token_requested_then_invalid_client() {
        HttpResponse<String> response = requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID, "not-the-secret",
                TasBaselineDataset.SPACE_CLIENT_SCOPE);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(error(response)).isEqualTo("invalid_client");
    }

    @Test
    void given_unknown_client_when_token_requested_then_invalid_client() {
        HttpResponse<String> response = requestToken("no-such-client", "whatever", "api.read");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(error(response)).isEqualTo("invalid_client");
    }

    @Test
    void given_client_authentication_refusals_then_all_answer_with_the_same_opaque_error() {
        // Secret invalide et client inconnu produisent une reponse identique, sans
        // error_description : la surface ne dit pas laquelle des deux causes s'applique.
        // Propriete voulue, a preserver.
        HttpResponse<String> wrongSecret = requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID, "not-the-secret",
                TasBaselineDataset.SPACE_CLIENT_SCOPE);
        HttpResponse<String> unknownClient =
                requestToken("no-such-client", "whatever", "api.read");

        assertThat(wrongSecret.body()).isEqualTo(unknownClient.body());
        assertThat(body(wrongSecret).has("error_description")).isFalse();
    }

    @Test
    void given_client_without_any_grant_type_when_token_requested_then_refused_as_unknown() {
        // TakiboRegisteredClientRepository refuse de construire un RegisteredClient sans grant
        // type et renvoie null : le client est traite comme introuvable plutot que de laisser
        // Spring Authorization Server lever une erreur opaque.
        UUID orphanUuid = UUID.fromString("dddddddd-0000-0000-0000-0000000000ff");
        jdbc.update("""
                INSERT INTO oauth2_clients (
                    id, org_id, space_id, client_id, client_name, client_type,
                    require_client_secret, client_secret_hash, token_endpoint_auth_method,
                    require_pkce, require_consent)
                VALUES (?, ?, ?, ?, ?, 'CONFIDENTIAL', TRUE, ?, 'client_secret_basic', FALSE, FALSE)
                """,
                orphanUuid, TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                "baseline-grantless-client", "Baseline Grantless Client",
                passwordEncoder.encode("grantless-secret"));

        HttpResponse<String> response = requestToken(
                "baseline-grantless-client", "grantless-secret", "api.read");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(error(response)).isEqualTo("invalid_client");
    }

    @Test
    void given_unregistered_scope_when_token_requested_then_invalid_scope() {
        // Le seul refus qui produit aujourd'hui une erreur OAuth2 conforme : la requete a
        // franchi l'authentification du client, donc Spring Authorization Server repond.
        HttpResponse<String> response = requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                "api.write");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(error(response)).isEqualTo("invalid_scope");
    }

    @Test
    void given_authorization_code_grant_when_token_requested_then_pkce_filter_rejects_on_stub_tenant() {
        // Comportement le plus revelateur du filet, et cible directe du recit 01.
        // Avec grant_type=authorization_code, PkceEnforcementFilter n'emprunte plus son
        // court-circuit : il cherche le client dans le tenant rendu par StubTenantResolver,
        // c'est-a-dire un couple org/space fixe qui n'est pas celui du client. Le client
        // existe pourtant bien en base.
        // Resultat : la requete est refusee par le filtre avant meme que Spring Authorization
        // Server ait pu repondre unauthorized_client. Tout client authorization_code reel
        // subira ce sort tant que le resolveur factice est en place.
        HttpResponse<String> response = requestGrant(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                "authorization_code");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(error(response)).isEqualTo("invalid_client");
        assertThat(errorDescription(response)).isEqualTo("Client not found");
        // Signature de OAuth2HttpErrorWriter : le refus vient bien du filtre Takibo,
        // et non de Spring Authorization Server.
        assertThat(header(response, "WWW-Authenticate")).contains("error=\"invalid_client\"");
        assertThat(header(response, "X-Trace-Id")).isNotBlank();
    }

    // ---------- Appel HTTP ----------

    private HttpResponse<String> requestToken(String clientId, String secret, String scope) {
        return post(clientId, secret, Map.of(
                "grant_type", "client_credentials",
                "scope", scope));
    }

    private HttpResponse<String> requestGrant(String clientId, String secret, String grantType) {
        return post(clientId, secret, Map.of("grant_type", grantType));
    }

    private HttpResponse<String> post(String clientId, String secret, Map<String, String> form) {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/oauth2/token"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(urlEncode(form)))
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

    private static String urlEncode(Map<String, String> form) {
        return new LinkedHashMap<>(form).entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String header(HttpResponse<String> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private static String accessToken(HttpResponse<String> response) {
        return body(response).path("access_token").asText();
    }

    private static String error(HttpResponse<String> response) {
        return body(response).path("error").asText();
    }

    private static String errorDescription(HttpResponse<String> response) {
        return body(response).path("error_description").asText();
    }

    private static JsonNode body(HttpResponse<String> response) {
        String raw = response.body();
        if (raw == null || raw.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Reponse /oauth2/token illisible: " + raw, e);
        }
    }
}
