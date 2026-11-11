package com.takibo.iamboot.tas;

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
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-HUNT-01 — un pouvoir délégué de cycle de vie ne restreint jamais un utilisateur plus
 * privilégié que son auteur, ni le propriétaire, ni son auteur lui-même.
 * <p>
 * Parcours réel de bout en bout sur PostgreSQL, sans donnée injectée dans les tables RBAC :
 * inscription (le fondateur est propriétaire et administrateur du Space), création des
 * employés et délégation de leurs rôles par l'API, connexions, puis restrictions de statut.
 * Chaque refus est vérifié en base : aucun statut ni version ne change.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class UserLifecycleProtectionPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID BOOTSTRAP_ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000b1");
    private static final UUID BOOTSTRAP_SPACE = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000b1");
    private static final UUID BOOTSTRAP_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000b1");
    private static final UUID BOOTSTRAP_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000b1");
    private static final String BOOTSTRAP_CODE = "hunt01-bootstrap-org";

    private static final String ORG_CODE = "hunt01-lifecycle-org";
    private static final String SPACE_CODE = "hunt01-space";
    private static final String PASSWORD = "Lifecycle!Pass1";
    private static final String USERS = "/api/v1/orgs/" + ORG_CODE + "/spaces/" + SPACE_CODE + "/users";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;

    private TasHttpFixture fixture;
    private String founderUserId;
    private String userAdminUserId;
    private String spaceAdminUserId;
    private String employeeUserId;
    private String founderToken;
    private String userAdminToken;
    private String spaceAdminToken;

    @BeforeEach
    void organizationWithOwnerDelegatesAndEmployee() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE, BOOTSTRAP_CODE);
        fixture.organizationWithSpaceAndAdmin(BOOTSTRAP_ORG, BOOTSTRAP_CODE, BOOTSTRAP_SPACE, "hunt01-bootstrap-space",
                BOOTSTRAP_ACCOUNT, BOOTSTRAP_USER);

        HttpResponse<String> signup = fixture.postJson("/api/v1/orgs/signup",
                fixture.organizationToken(BOOTSTRAP_ORG, BOOTSTRAP_ACCOUNT), """
                {"organization":{"code":"%s","name":"Hunt 01"},
                 "space":{"code":"%s","name":"Hunt 01 Space"},
                 "account":{"email":"founder@hunt01.test","password":"%s"},
                 "profile":{"username":"hunt01-founder","firstName":"Hunt","lastName":"Founder"}}"""
                        .formatted(ORG_CODE, SPACE_CODE, PASSWORD));
        assertThat(signup.statusCode()).as("inscription : %s", signup.body()).isEqualTo(201);
        founderUserId = TasHttpFixture.body(signup).path("userId").asText();
        founderToken = login("founder@hunt01.test");

        userAdminUserId = employee("user-admin", "R_SPACE_USER_ADMIN");
        spaceAdminUserId = employee("space-admin", "R_SPACE_ADMIN");
        employeeUserId = employee("employee", null);
        userAdminToken = login("user-admin@hunt01.test");
        spaceAdminToken = login("space-admin@hunt01.test");
    }

    @AfterEach
    void cleanUp() {
        fixture.purgeOrganizations(ORG_CODE, BOOTSTRAP_CODE);
    }

    // ---------- Ce qui reste permis : la délégation n'est pas neutralisée ----------

    @Test
    void a_user_administrator_still_deactivates_an_ordinary_user_who_can_no_longer_log_in() {
        HttpResponse<String> response = restrict("deactivate", employeeUserId, userAdminToken);

        assertThat(response.statusCode()).as("désactivation d'un employé : %s", response.body()).isEqualTo(200);
        assertThat(status(employeeUserId)).isEqualTo("DEACTIVATED");
        assertThat(loginStatus("employee@hunt01.test")).as("nouveau login de l'employé désactivé").isEqualTo(401);
    }

    @Test
    void a_user_administrator_still_suspends_then_reactivates_an_ordinary_user() {
        assertThat(restrict("suspend", employeeUserId, userAdminToken).statusCode()).isEqualTo(200);
        assertThat(restrict("activate", employeeUserId, userAdminToken).statusCode()).isEqualTo(200);
        assertThat(status(employeeUserId)).isEqualTo("ACTIVE");
    }

    @Test
    void the_owner_still_deactivates_a_space_administrator() {
        HttpResponse<String> response = restrict("deactivate", spaceAdminUserId, founderToken);

        assertThat(response.statusCode()).as("le propriétaire désactive un administrateur : %s", response.body())
                .isEqualTo(200);
        assertThat(status(spaceAdminUserId)).isEqualTo("DEACTIVATED");
    }

    // ---------- Rouge sur main : HUNT-01 ----------

    @ParameterizedTest
    @ValueSource(strings = {"suspend", "lock", "deactivate"})
    void a_user_administrator_never_restricts_the_owner(String action) {
        assertRefusedWithoutMutation(action, founderUserId, userAdminToken, 403);
        assertThat(loginStatus("founder@hunt01.test")).as("le fondateur se connecte toujours au Space").isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"suspend", "lock", "deactivate"})
    void a_user_administrator_never_restricts_a_space_administrator(String action) {
        assertRefusedWithoutMutation(action, spaceAdminUserId, userAdminToken, 403);
    }

    @Test
    void a_space_administrator_never_restricts_the_owner() {
        assertRefusedWithoutMutation("deactivate", founderUserId, spaceAdminToken, 403);
    }

    @Test
    void nobody_restricts_themselves_on_this_surface() {
        assertRefusedWithoutMutation("deactivate", userAdminUserId, userAdminToken, 409);
        assertRefusedWithoutMutation("suspend", spaceAdminUserId, spaceAdminToken, 409);
    }

    // ---------- Jetons après désactivation ----------

    /**
     * Limite connue, hors de ce récit (invariant B, IAM-REVOKE-02) : un token humain n'est pas
     * révocable. Ce test fixe le comportement actuel pour qu'un changement soit visible.
     */
    @Test
    void a_token_issued_before_deactivation_keeps_working_until_it_expires_known_limit() {
        assertThat(restrict("deactivate", userAdminUserId, founderToken).statusCode()).isEqualTo(200);

        assertThat(loginStatus("user-admin@hunt01.test")).as("aucun nouveau token").isEqualTo(401);
        assertThat(fixture.send("GET", USERS, userAdminToken, Map.of(), null).statusCode())
                .as("token émis avant la désactivation : encore accepté jusqu'à son expiration (IAM-REVOKE-02)")
                .isEqualTo(200);
    }

    // ---------- Outils ----------

    private void assertRefusedWithoutMutation(String action, String targetUserId, String token, int expected) {
        String before = stateOf(targetUserId);

        HttpResponse<String> response = restrict(action, targetUserId, token);

        assertThat(response.statusCode()).as("%s de %s, réponse brute : %s", action, targetUserId, response.body())
                .isEqualTo(expected);
        assertThat(stateOf(targetUserId)).as("statut et version après le refus").isEqualTo(before);
    }

    private HttpResponse<String> restrict(String action, String userId, String token) {
        return fixture.postJson(USERS + "/" + userId + "/" + action, token, "{\"reason\":\"SEC-HUNT-01\"}");
    }

    private String employee(String name, String roleCode) {
        HttpResponse<String> created = fixture.postJson(USERS, founderToken, """
                {"email":"%s@hunt01.test","rawPassword":"%s","username":"hunt01-%s","firstName":"H","lastName":"U"}"""
                .formatted(name, PASSWORD, name));
        assertThat(created.statusCode()).as("création de %s : %s", name, created.body()).isEqualTo(201);
        String userId = TasHttpFixture.body(created).path("id").asText();
        if (roleCode != null) {
            HttpResponse<String> delegation = fixture.postJson(USERS + "/" + userId + "/roles", founderToken,
                    "{\"roleCode\":\"" + roleCode + "\",\"reason\":\"SEC-HUNT-01\"}");
            assertThat(delegation.statusCode()).as("attribution de %s : %s", roleCode, delegation.body())
                    .isEqualTo(200);
        }
        return userId;
    }

    private String status(String userId) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, UUID.fromString(userId));
    }

    private String stateOf(String userId) {
        return jdbc.queryForObject("SELECT status || '#' || version FROM users WHERE id = ?", String.class,
                UUID.fromString(userId));
    }

    private int loginStatus(String email) {
        return fixture.postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s","orgCode":"%s","spaceCode":"%s"}"""
                .formatted(email, PASSWORD, ORG_CODE, SPACE_CODE)).statusCode();
    }

    private String login(String email) {
        HttpResponse<String> response = fixture.postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s","orgCode":"%s","spaceCode":"%s"}"""
                .formatted(email, PASSWORD, ORG_CODE, SPACE_CODE));
        assertThat(response.statusCode()).as("connexion de %s : %s", email, response.body()).isEqualTo(200);
        return TasHttpFixture.body(response).path("accessToken").asText();
    }
}
