package com.takibo.iamboot.ui;

import com.takibo.securitymanagement.infrastructure.token.TokenValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TAKIBO-UI-BOOT-01 — l'interface est servie par le vrai serveur, sans affaiblir la sécurité API.
 * {@code src/test/resources/static} remplace le build Vite, que {@code bootJar} seul embarque.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.mail.enabled=false")
@ActiveProfiles("test")
class UiServingIntegrationTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private TokenValidatorAdapter tokenValidatorAdapter;

    @MockitoBean(name = "messagingHealthIndicator")
    private HealthIndicator messagingHealthIndicator;

    @MockitoBean(name = "outboxHealthIndicator")
    private HealthIndicator outboxHealthIndicator;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        when(messagingHealthIndicator.health()).thenReturn(Health.up().build());
        when(outboxHealthIndicator.health()).thenReturn(Health.up().build());
    }

    @Test
    void homePage_isServed() throws Exception {
        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("TAKIBO test");
    }

    @Test
    void spaRoutes_areServedOnDirectRefresh() throws Exception {
        for (String route : new String[] {"/login", "/app", "/app/dashboard", "/app/spaces"}) {
            HttpResponse<String> response = get(route);
            assertThat(response.statusCode()).as(route).isEqualTo(200);
            assertThat(response.body()).as(route).contains("TAKIBO test");
        }
    }

    @Test
    void assets_areServedWithTheirOwnContentType() throws Exception {
        HttpResponse<String> response = get("/assets/app.js");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("javascript");
    }

    @Test
    void missingAsset_isA404NotTheHtmlShell() throws Exception {
        HttpResponse<String> response = get("/assets/absent.js");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("TAKIBO test");
    }

    @Test
    void protectedApi_withoutAuthentication_isRefused() throws Exception {
        HttpResponse<String> response = get("/api/v1/me/spaces");

        assertThat(response.statusCode()).isIn(401, 403);
        assertThat(response.body()).doesNotContain("TAKIBO test");
    }

    @Test
    void unknownApiRoute_neverFallsBackToHtml() throws Exception {
        for (String path : new String[] {"/api/v1/inexistant", "/api/inexistant", "/actuator/inexistant"}) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as(path).isIn(401, 403, 404);
            assertThat(response.body()).as(path).doesNotContain("TAKIBO test");
        }
    }

    @Test
    void actuatorHealth_staysPublic_otherEndpointsStayReserved() throws Exception {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/env").statusCode()).isIn(401, 403);
    }

    @Test
    void uiChain_isReadOnly() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(uri("/login"))
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(401, 403, 405);
        assertThat(response.body()).doesNotContain("TAKIBO test");
    }

    @Test
    void unlistedPath_staysDeniedByTheApiChain() throws Exception {
        HttpResponse<String> response = get("/index.html.bak");

        assertThat(response.statusCode()).isIn(401, 403);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
