package com.takibo.iamboot.tas;

import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-TMS-07 (D1) — aucune surface HTTP de diagnostic n'est exposée, y compris avec le profil
 * {@code dev} activé en plus du profil {@code test}. Les mappings sont lus dans l'application
 * démarrée, et les anciens chemins sont rejoués en HTTP : ils ne répondent jamais 200.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles({"test", "dev"})
@EnabledIf("dockerIsAvailable")
class NoDebugEndpointInDevProfilePostgresIntegrationTest extends TasPostgresBaseline {

    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;

    @Test
    void no_debug_endpoint_is_mapped_with_the_dev_profile() {
        List<String> patterns = new ArrayList<>();
        context.getBeansOfType(RequestMappingHandlerMapping.class).values().forEach(mapping ->
                mapping.getHandlerMethods().keySet().forEach(info -> patterns.addAll(info.getPatternValues())));

        assertThat(patterns).as("mappings lus à l'exécution").isNotEmpty()
                .noneMatch(pattern -> pattern.startsWith("/debug"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/debug/secure/context", "/debug/secure/hello", "/debug/secure/user-read"})
    void former_debug_paths_never_answer(String path) {
        TasHttpFixture fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        String token = fixture.spaceToken(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of("P_SPACE_USERS_READ"));

        assertThat(fixture.send("GET", path, null, Map.of(), null).statusCode()).as("anonyme").isEqualTo(401);
        assertThat(fixture.send("GET", path, token, Map.of(), null).statusCode()).as("authentifié").isEqualTo(403);
    }
}
