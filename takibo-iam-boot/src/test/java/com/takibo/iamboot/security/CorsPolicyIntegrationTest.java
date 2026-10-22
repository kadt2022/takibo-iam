package com.takibo.iamboot.security;

import com.takibo.securitymanagement.infrastructure.token.TokenValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SEC-TMS-05 — politique CORS traversant le vrai serveur : filtres servlet enregistrés sur
 * {@code /oauth2/*}, puis chaînes Spring Security API et TAS. MockMvc ne traverserait pas les
 * filtres servlet, qui passent avant Spring Security.
 *
 * <p>Le profil {@code test} n'accepte que {@value #CONFIGURED}. {@code java.net.http.HttpClient}
 * plutôt que {@code HttpURLConnection}, qui refuse de poser l'en-tête {@code Origin}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.mail.enabled=false")
@ActiveProfiles("test")
class CorsPolicyIntegrationTest {

    private static final String CONFIGURED = "https://portail.example.test";
    private static final String UNKNOWN = "https://inconnu.example.test";

    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private TokenValidatorAdapter tokenValidatorAdapter;

    @MockitoBean(name = "messagingHealthIndicator")
    private HealthIndicator messagingHealthIndicator;

    @MockitoBean(name = "outboxHealthIndicator")
    private HealthIndicator outboxHealthIndicator;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        when(tokenValidatorAdapter.validate("platform-admin-token"))
                .thenReturn(platformAdminClaims());
        when(messagingHealthIndicator.health()).thenReturn(Health.up().build());
        when(outboxHealthIndicator.health()).thenReturn(Health.up().build());
    }

    @Test
    void ac01_preflightFromConfiguredOrigin_isAcceptedWithoutWildcardNorCredentials() throws Exception {
        HttpResponse<String> response = preflight("/api/v1/auth/login", CONFIGURED, "POST", "authorization, content-type");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(CONFIGURED);
        assertThat(response.headers().allValues("Vary")).anySatisfy(vary -> assertThat(vary).contains("Origin"));
        assertNoCredentials(response);
    }

    @Test
    void ac02_realRequestFromConfiguredOrigin_keepsItsNominalStatus() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/actuator/env"))
                .header("Origin", CONFIGURED)
                .header("Authorization", "Bearer platform-admin-token")
                .GET());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(CONFIGURED);
        assertNoCredentials(response);
    }

    @Test
    void ac03_preflightFromUnknownOrigin_isRefused() throws Exception {
        assertRefused(preflight("/api/v1/auth/login", UNKNOWN, "POST", "authorization, content-type"));
    }

    @Test
    void ac04_realRequestFromUnknownOrigin_isRefusedEvenWithAValidToken() throws Exception {
        assertRefused(send(HttpRequest.newBuilder(uri("/actuator/env"))
                .header("Origin", UNKNOWN)
                .header("Authorization", "Bearer platform-admin-token")
                .GET()));
    }

    @Test
    void ac05_neighboursOfTheConfiguredOrigin_areRefused() throws Exception {
        for (String neighbour : List.of(
                "https://portail.example.test.inconnu.test",
                "https://faux-portail.example.test",
                "https://example.test",
                "http://portail.example.test",
                "https://portail.example.test:8443",
                "null")) {
            HttpResponse<String> response = preflight("/api/v1/auth/login", neighbour, "POST", "content-type");
            assertThat(response.statusCode()).as(neighbour).isEqualTo(403);
            assertThat(header(response, ALLOW_ORIGIN)).as(neighbour).isNull();
        }
    }

    @Test
    void ac06_callsWithoutOrigin_areUnchanged() throws Exception {
        HttpResponse<String> health = send(HttpRequest.newBuilder(uri("/actuator/health")).GET());
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(header(health, ALLOW_ORIGIN)).isNull();

        HttpResponse<String> env = send(HttpRequest.newBuilder(uri("/actuator/env"))
                .header("Authorization", "Bearer platform-admin-token")
                .GET());
        assertThat(env.statusCode()).isEqualTo(200);

        HttpResponse<String> jwks = send(HttpRequest.newBuilder(uri("/oauth2/jwks")).GET());
        assertThat(jwks.statusCode()).isEqualTo(200);
        assertThat(header(jwks, ALLOW_ORIGIN)).isNull();
    }

    @Test
    void ac07_tasChain_appliesTheSameListExplicitly() throws Exception {
        // Aucune source de type UrlBasedCorsConfigurationSource : applyCorsIfAvailable de Spring
        // Security ne peut rien appliquer d'office. Une chaîne qui a un CorsFilter l'a déclaré.
        assertThat(context.getBeanNamesForType(UrlBasedCorsConfigurationSource.class)).isEmpty();
        List<SecurityFilterChain> chains = context.getBean(FilterChainProxy.class).getFilterChains();
        assertThat(chains).hasSizeGreaterThanOrEqualTo(2)
                .allSatisfy(chain -> assertThat(chain.getFilters()).hasAtLeastOneElementOfType(CorsFilter.class));

        // POST /oauth2/token en formulaire, sans Authorization : requête CORS simple, sans
        // preflight — la forme de l'échange de code d'une SPA publique (TAS-GRANTS-04). Servie
        // par la chaîne TAS, dont le CORS refuse l'origine inconnue avant toute authentification
        // du client.
        assertRefused(tokenRequest(UNKNOWN));

        HttpResponse<String> configured = tokenRequest(CONFIGURED);
        assertThat(configured.statusCode()).isNotEqualTo(403);
        assertThat(header(configured, ALLOW_ORIGIN)).isEqualTo(CONFIGURED);
        assertNoCredentials(configured);

        // Le preflight ne porte pas de client_id : TenantResolutionFilter, filtre servlet qui
        // passe avant Spring Security, le rejette en invalid_client, sans en-tête CORS, quelle
        // que soit l'origine. Le navigateur refuse donc tout appel /oauth2/token qui exigerait
        // un preflight, dont un secret client en Basic. Comportement consigné, pas modifié :
        // TAS-GRANTS-04 décide s'il faut l'ouvrir.
        for (String origin : List.of(CONFIGURED, UNKNOWN)) {
            HttpResponse<String> tokenPreflight = preflight("/oauth2/token", origin, "POST", "authorization, content-type");
            assertThat(tokenPreflight.statusCode()).as(origin).isEqualTo(401);
            assertThat(header(tokenPreflight, ALLOW_ORIGIN)).as(origin).isNull();
            assertNoCredentials(tokenPreflight);
        }
    }

    @Test
    void ac12_patchPreflight_isAccepted() throws Exception {
        HttpResponse<String> response = preflight("/api/v1/users/" + USER_ID + "/status", CONFIGURED, "PATCH",
                "authorization, content-type");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(CONFIGURED);
        assertThat(header(response, "Access-Control-Allow-Methods")).contains("PATCH");
        assertNoCredentials(response);
    }

    private HttpResponse<String> tokenRequest(String origin) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri("/oauth2/token"))
                .header("Origin", origin)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&client_id=postman-client")));
    }

    private HttpResponse<String> preflight(String path, String origin, String method, String requestHeaders)
            throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", requestHeaders)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static void assertRefused(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(header(response, ALLOW_ORIGIN)).isNull();
        assertNoCredentials(response);
    }

    // AC-11 : vérifié sur chaque réponse, acceptée ou refusée, des deux chaînes.
    private static void assertNoCredentials(HttpResponse<String> response) {
        assertThat(header(response, ALLOW_CREDENTIALS)).isNull();
    }

    private static String header(HttpResponse<String> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private static Map<String, Object> platformAdminClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", USER_ID.toString());
        claims.put("subjectType", "HUMAN");
        claims.put("authMethod", "PASSWORD");
        claims.put("userId", USER_ID.toString());
        claims.put("roles", List.of("R_TAKIBO_PLATFORM_ADMIN"));
        claims.put("scopeLevel", "PLATFORM");
        return claims;
    }
}
