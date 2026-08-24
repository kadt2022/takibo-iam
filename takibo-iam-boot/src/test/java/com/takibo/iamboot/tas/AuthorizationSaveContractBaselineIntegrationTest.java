package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fige ce que Spring Authorization Server confie a {@code OAuth2AuthorizationService.save()}
 * apres une emission {@code client_credentials} reussie (TAS-GRANTS-00).
 * <p>
 * L'application ne declare aucun bean de ce type : SAS fabrique son implementation en memoire
 * et la garde comme objet partage de son configurer, hors du contexte. Le contrat de
 * sauvegarde est donc inobservable en l'etat.
 * <p>
 * Ce test declare un service enregistreur <b>cote test uniquement</b>. SAS traite un bean
 * fourni exactement comme son implementation par defaut : ce sont les memes providers qui
 * appellent {@code save()}, avec les memes arguments. Ce qui est observe ici est donc bien le
 * contrat de production, pas un artefact du test.
 * <p>
 * C'est ce contrat que le service persistant du recit 02 devra honorer a l'identique. Deux
 * points meritent l'attention a ce moment-la :
 * <ul>
 *   <li>{@code registeredClientId} porte l'identifiant technique du client, jamais le
 *       {@code client_id} public — or la contrainte {@code fk_oauth2_authz_client_scope}
 *       reference aujourd'hui le second ;</li>
 *   <li>le client PLATFORM produit une autorisation sans organisation ni space, alors que
 *       {@code oauth2_authorization.org_id} est aujourd'hui {@code NOT NULL}.</li>
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

    @TestConfiguration
    static class RecordingAuthorizationServiceConfiguration {
        @Bean
        RecordingOAuth2AuthorizationService oAuth2AuthorizationService() {
            return new RecordingOAuth2AuthorizationService();
        }
    }

    /** Delegue au service en memoire de SAS et conserve ce qui lui est confie. */
    static class RecordingOAuth2AuthorizationService implements OAuth2AuthorizationService {

        private final OAuth2AuthorizationService delegate = new InMemoryOAuth2AuthorizationService();
        private final List<OAuth2Authorization> saved = new CopyOnWriteArrayList<>();

        @Override
        public void save(OAuth2Authorization authorization) {
            saved.add(authorization);
            delegate.save(authorization);
        }

        @Override
        public void remove(OAuth2Authorization authorization) {
            delegate.remove(authorization);
        }

        @Override
        public OAuth2Authorization findById(String id) {
            return delegate.findById(id);
        }

        @Override
        public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
            return delegate.findByToken(token, tokenType);
        }

        List<OAuth2Authorization> saved() {
            return List.copyOf(saved);
        }

        void reset() {
            saved.clear();
        }
    }

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RecordingOAuth2AuthorizationService authorizationService;

    @Value("${takibo.dev.postman-client.secret}")
    private String platformClientSecret;

    private TasBaselineDataset dataset;

    @BeforeEach
    void seed() {
        dataset = new TasBaselineDataset(jdbc, passwordEncoder);
        dataset.reset();
        authorizationService.reset();
    }

    @Test
    void given_platform_success_then_one_authorization_is_saved_without_any_tenant() {
        HttpResponse<String> response = requestToken(
                TasBaselineDataset.PLATFORM_CLIENT_ID, platformClientSecret, "api.read");
        assertThat(response.statusCode()).isEqualTo(200);

        assertThat(authorizationService.saved()).hasSize(1);
        OAuth2Authorization authorization = authorizationService.saved().get(0);

        assertThat(authorization.getPrincipalName())
                .isEqualTo(TasBaselineDataset.PLATFORM_CLIENT_ID);
        assertThat(authorization.getAuthorizationGrantType().getValue())
                .isEqualTo("client_credentials");
        assertThat(authorization.getAuthorizedScopes()).containsExactly("api.read");

        // Le client PLATFORM est in-memory : son identifiant technique ne correspond a
        // aucune ligne de oauth2_clients. Persister cette autorisation telle quelle
        // violerait fk_oauth2_authz_client_scope, en plus de org_id NOT NULL. Le recit 02
        // doit traiter ce cas, pas le contourner.
        Long matchingClientRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oauth2_clients WHERE id = CAST(? AS uuid)",
                Long.class, authorization.getRegisteredClientId());
        assertThat(matchingClientRows).isZero();
    }

    @Test
    void given_space_success_then_saved_authorization_uses_the_technical_client_identifier() {
        HttpResponse<String> response = requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE);
        assertThat(response.statusCode()).isEqualTo(200);

        assertThat(authorizationService.saved()).hasSize(1);
        OAuth2Authorization authorization = authorizationService.saved().get(0);

        // Identifiant technique, jamais le client_id public : le schema actuel fait pourtant
        // pointer fk_oauth2_authz_client_scope vers oauth2_clients.client_id.
        assertThat(authorization.getRegisteredClientId())
                .isEqualTo(TasBaselineDataset.SPACE_CLIENT_UUID.toString());
        assertThat(authorization.getPrincipalName())
                .isEqualTo(TasBaselineDataset.SPACE_CLIENT_ID);
        assertThat(authorization.getAuthorizedScopes())
                .containsExactly(TasBaselineDataset.SPACE_CLIENT_SCOPE);
    }

    @Test
    void given_success_then_saved_access_token_matches_the_one_returned_to_the_client() {
        HttpResponse<String> response = requestToken(
                TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET,
                TasBaselineDataset.SPACE_CLIENT_SCOPE);
        String returned = accessToken(response);

        OAuth2Authorization authorization = authorizationService.saved().get(0);
        OAuth2Authorization.Token<OAuth2AccessToken> token =
                authorization.getToken(OAuth2AccessToken.class);

        assertThat(token).isNotNull();
        assertThat(token.getToken().getTokenValue()).isEqualTo(returned);
        assertThat(token.getToken().getIssuedAt()).isNotNull();
        assertThat(token.getToken().getExpiresAt()).isNotNull();
        // Aucun refresh token en client_credentials : la RFC l'interdit.
        assertThat(authorization.getRefreshToken()).isNull();
    }

    @Test
    void given_refused_request_then_nothing_is_saved() {
        requestToken(TasBaselineDataset.SPACE_CLIENT_ID, "not-the-secret",
                TasBaselineDataset.SPACE_CLIENT_SCOPE);

        assertThat(authorizationService.saved()).isEmpty();
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
