package com.takibo.iamboot.security;

import com.takibo.securitymanagement.infrastructure.token.TokenValidatorAdapter;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * JwtAuthenticationFilter et OrgBoundaryFilter ne s'exécutent que dans la chaîne de sécurité
 * API. Composants Spring, ils étaient aussi montés par Spring Boot comme filtres servlet sur
 * {@code /*} : OrgBoundaryFilter interrogeait alors l'appartenance sur n'importe quelle URL
 * portant un {@code spaceId}, page de connexion comprise, et révélait l'existence d'un space
 * à un anonyme (403 s'il existe, 404 sinon). Vérifié sur le vrai serveur : MockMvc ne monte
 * pas les filtres servlet.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.mail.enabled=false")
@ActiveProfiles("test")
class SecurityFilterServletRegistrationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ServletContext servletContext;

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
    void securityFilters_areNotMountedAsServletFilters() {
        assertThat(servletContext.getFilterRegistrations().keySet())
                .doesNotContain("jwtAuthenticationFilter", "orgBoundaryFilter")
                .contains("springSecurityFilterChain");
    }

    @Test
    void anonymousLoginPage_withASpaceIdParameter_isServed_withoutAnyBoundaryCheck() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login?spaceId=" + UUID.randomUUID()))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("TAKIBO test");
    }
}
