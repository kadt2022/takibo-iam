package com.takibo.iamboot.tas;

import com.takibo.adp.api.AdaptiveDecisionPort;
import com.takibo.adp.api.Decision;
import com.takibo.adp.api.DecisionResponse;
import com.takibo.authorizationserver.infrastructure.springauthserver.properties.TasAuthorizationServerProperties;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SEC-TMS-07 — TAKIBO sécurise par lui-même. L'ADP est une protection complémentaire,
 * facultative et configurable. Il ne crée jamais l'autorité.
 * <p>
 * L'ADP est remplacé par un composant qui autorise TOUJOURS : c'est un outil de preuve, pas
 * l'architecture cible. Chaque refus mesuré ici vient des seuls contrôles déterministes, et le
 * test vérifie qu'un refus est prononcé sans même consulter l'ADP. Une route qu'aucune
 * politique ne gouverne doit être refusée, et non atteindre son contrôleur.
 * <p>
 * Chaîne réelle de bout en bout sur PostgreSQL.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class DeterministicAuthorizationWithPermissiveAdpPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID ORG_A = TasBaselineDataset.ORG_ID;
    private static final UUID SPACE_A = TasBaselineDataset.SPACE_ID;
    private static final UUID ACCOUNT_A = TasBaselineDataset.ACCOUNT_ID;
    private static final UUID SPACE_A2 = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000c7");
    private static final UUID ORG_B = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000c7");
    private static final UUID SPACE_B = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000c8");
    private static final UUID ACCOUNT_B = UUID.fromString("cccccccc-0000-0000-0000-0000000000c7");
    private static final UUID USER_B = UUID.fromString("dddddddd-0000-0000-0000-0000000000c7");
    private static final String ORG_B_CODE = "permissive-adp-org-b";

    private static final String READABLE_SPACE_A =
            "/api/v1/orgs/" + TasBaselineDataset.ORG_CODE + "/spaces/" + TasBaselineDataset.SPACE_CODE;
    private static final String USERS_A = READABLE_SPACE_A + "/users";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private TasAuthorizationServerProperties tasProperties;

    /** ADP systématiquement permissif : il ne refuse jamais rien. */
    @MockitoBean private AdaptiveDecisionPort adaptiveDecisionPort;

    private TasHttpFixture fixture;
    private TasBaselineDataset dataset;

    @BeforeEach
    void twoOrganizationsAndAPermissiveAdp() {
        when(adaptiveDecisionPort.evaluate(any())).thenReturn(new DecisionResponse("permissive-test-adp",
                Decision.ALLOW, 0.0, 1.0, 0.0, "Always allow (SEC-TMS-07 test)", null, List.of(), null,
                Instant.now(), 0L));
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_B_CODE);
        dataset = new TasBaselineDataset(jdbc, passwordEncoder);
        dataset.reset();
        jdbc.update("INSERT INTO spaces (id, org_id, code, name, status) VALUES (?, ?, 'permissive-a2', 'A2', 'ACTIVE')",
                SPACE_A2, ORG_A);
        fixture.organizationWithSpaceAndAdmin(ORG_B, ORG_B_CODE, SPACE_B, "permissive-space-b", ACCOUNT_B, USER_B);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM spaces WHERE id = ?", SPACE_A2);
        fixture.purgeOrganizations(ORG_B_CODE);
        dataset.clear();
    }

    // ---------- Contrôle : l'ADP permissif est bien celui qui est consulté ----------

    @Test
    void a_legitimate_request_passes_and_the_permissive_adp_is_really_consulted() {
        assertThat(get(USERS_A, spaceToken(ORG_A, SPACE_A, List.of("P_SPACE_USERS_READ")))).isEqualTo(200);

        verify(adaptiveDecisionPort, atLeastOnce()).evaluate(any());
    }

    // ---------- Rouge : l'absence de politique n'est jamais une autorisation ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/future-surface",
            "/api/v1/orgs/baseline-org/members",
            "/api/v1/orgs/baseline-org/spaces/baseline-space/audit"
    })
    void a_route_that_no_policy_governs_is_refused_even_with_every_permission(String ungoverned) {
        String everything = spaceToken(ORG_A, SPACE_A, List.of(
                "P_SPACE_USERS_READ", "P_SPACE_USERS_MANAGE", "P_SPACE_RBAC_READ", "P_SPACE_RBAC_ASSIGN"));

        assertThat(get(ungoverned, everything))
                .as("une route sans politique doit être refusée avant son contrôleur, pas répondre 404")
                .isEqualTo(403);
        assertAdpNeverConsulted();
    }

    // ---------- Garde-fous : l'isolation tient sans l'ADP ----------

    @Test
    void a_token_of_another_organization_never_reads_organization_a() {
        assertThat(get(USERS_A, spaceToken(ORG_B, SPACE_B, List.of("P_SPACE_USERS_READ")))).isIn(403, 404);
    }

    @Test
    void a_deterministic_refusal_is_pronounced_without_consulting_the_adp() {
        assertThat(get(USERS_A, spaceToken(ORG_A, SPACE_A, List.of()))).isEqualTo(403);
        assertThat(get(USERS_A, sign(claims("ORGANIZATION", ORG_A, null, List.of("P_ORG_USERS_READ"))))).isEqualTo(403);
        assertThat(get("/api/v1/orgs/" + ORG_A + "/spaces",
                sign(claims("ORGANIZATION", ORG_B, null, List.of("P_ORG_SPACES_READ"))))).isEqualTo(403);

        assertAdpNeverConsulted();
    }

    @Test
    void a_token_of_another_space_of_the_same_organization_never_reads_space_a() {
        assertThat(get(USERS_A, spaceToken(ORG_A, SPACE_A2, List.of("P_SPACE_USERS_READ")))).isIn(403, 404);
    }

    @Test
    void an_organization_token_never_uses_a_space_route() {
        assertThat(get(USERS_A, sign(claims("ORGANIZATION", ORG_A, null, List.of("P_ORG_USERS_READ"))))).isEqualTo(403);
    }

    @Test
    void a_space_token_without_the_required_permission_is_refused() {
        assertThat(get(USERS_A, spaceToken(ORG_A, SPACE_A, List.of()))).isEqualTo(403);
    }

    /**
     * Un token humain PLATFORM portant account_id sans org_id est rejeté dès l'authentification
     * (TakiboSecurityContextFactory) : 401. Le refus est déterministe, avant même l'ADP.
     */
    @Test
    void a_platform_token_never_reaches_a_tenant_route() {
        String platform = sign(claims("PLATFORM", null, null, List.of()).claim("roles", List.of("R_TAKIBO_PLATFORM_ADMIN")));

        assertThat(get(USERS_A, platform)).isIn(401, 403);
        assertThat(get("/api/v1/orgs/" + ORG_A + "/spaces", platform)).isIn(401, 403);
    }

    @Test
    void a_tms_uuid_route_of_another_organization_is_refused() {
        assertThat(get("/api/v1/orgs/" + ORG_A + "/spaces",
                sign(claims("ORGANIZATION", ORG_B, null, List.of("P_ORG_SPACES_READ"))))).isEqualTo(403);
    }

    @Test
    void authentication_failures_stay_401() {
        assertThat(get(USERS_A, null)).isEqualTo(401);
        String forged = spaceToken(ORG_A, SPACE_A, List.of("P_SPACE_USERS_READ"));
        assertThat(get(USERS_A, forged.substring(0, forged.length() - 4) + "AAAA")).isEqualTo(401);
    }

    @Test
    void public_routes_keep_their_contract() {
        assertThat(fixture.postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"Wrong!Pass1","orgCode":"%s"}"""
                .formatted(TasBaselineDataset.ACCOUNT_EMAIL, TasBaselineDataset.ORG_CODE)).statusCode()).isEqualTo(401);
        assertThat(get("/actuator/health", null)).isEqualTo(200);
    }

    // ---------- Outils ----------

    private void assertAdpNeverConsulted() {
        verify(adaptiveDecisionPort, never()).evaluate(any());
    }

    private int get(String path, String token) {
        return fixture.send("GET", path, token, Map.of(), null).statusCode();
    }

    private String spaceToken(UUID orgId, UUID spaceId, List<String> permissions) {
        return sign(claims("SPACE", orgId, spaceId, permissions));
    }

    /** Mêmes claims que l'émetteur humain, pour fabriquer chaque forme de token sans ses contrôles. */
    private JwtClaimsSet.Builder claims(String level, UUID orgId, UUID spaceId, List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(tasProperties.getIssuer())
                .subject(ACCOUNT_A.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("subject_type", "HUMAN")
                .claim("auth_method", "PASSWORD")
                .claim("takibo_scope_level", level)
                .claim("account_id", ACCOUNT_A.toString())
                .claim("roles", List.of())
                .claim("groups", List.of())
                .claim("permissions", permissions);
        if (orgId != null) {
            claims.claim("org_id", orgId.toString());
        }
        if (spaceId != null) {
            claims.claim("space_id", spaceId.toString()).claim("user_id", UUID.randomUUID().toString());
        }
        return claims;
    }

    private String sign(JwtClaimsSet.Builder claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }
}
