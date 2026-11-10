package com.takibo.iamboot.tas;

import com.takibo.authorizationserver.infrastructure.springauthserver.properties.TasAuthorizationServerProperties;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-TMS-07 — TAKIBO sécurise par lui-même, dans sa configuration par défaut.
 * <p>
 * Aucun composant n'est simulé : l'ADP est celui de l'application, désactivé par défaut
 * ({@code takibo.adp.enabled=false}), et un ADP désactivé répond ALLOW. Une route qu'aucune
 * politique ne gouverne doit pourtant être refusée par le seul socle déterministe.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class DeterministicAuthorizationWithDisabledAdpPostgresIntegrationTest extends TasPostgresBaseline {

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private TasAuthorizationServerProperties tasProperties;
    @Value("${takibo.adp.enabled}") private boolean adpEnabled;

    private TasHttpFixture fixture;
    private TasBaselineDataset dataset;

    @BeforeEach
    void baseline() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        dataset = new TasBaselineDataset(jdbc, passwordEncoder);
        dataset.reset();
    }

    @AfterEach
    void cleanUp() {
        dataset.clear();
    }

    @Test
    void the_adp_is_disabled_in_the_default_configuration() {
        assertThat(adpEnabled).as("configuration par défaut mesurée").isFalse();
    }

    @Test
    void a_legitimate_request_still_passes() {
        assertThat(get("/api/v1/orgs/" + TasBaselineDataset.ORG_CODE + "/spaces/" + TasBaselineDataset.SPACE_CODE
                + "/users", List.of("P_SPACE_USERS_READ"))).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/future-surface",
            "/api/v1/orgs/baseline-org/members",
            "/api/v1/orgs/baseline-org/spaces/baseline-space/audit"
    })
    void a_route_that_no_policy_governs_is_refused_by_the_core_alone(String ungoverned) {
        assertThat(get(ungoverned, List.of("P_SPACE_USERS_READ", "P_SPACE_USERS_MANAGE", "P_SPACE_RBAC_READ",
                "P_SPACE_RBAC_ASSIGN"))).isEqualTo(403);
    }

    private int get(String path, List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(tasProperties.getIssuer())
                .subject(TasBaselineDataset.ACCOUNT_ID.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("subject_type", "HUMAN")
                .claim("auth_method", "PASSWORD")
                .claim("takibo_scope_level", "SPACE")
                .claim("org_id", TasBaselineDataset.ORG_ID.toString())
                .claim("space_id", TasBaselineDataset.SPACE_ID.toString())
                .claim("user_id", UUID.randomUUID().toString())
                .claim("account_id", TasBaselineDataset.ACCOUNT_ID.toString())
                .claim("roles", List.of())
                .claim("groups", List.of())
                .claim("permissions", permissions)
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return fixture.send("GET", path, token, Map.of(), null).statusCode();
    }
}
