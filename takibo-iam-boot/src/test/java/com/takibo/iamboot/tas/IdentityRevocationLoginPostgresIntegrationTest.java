package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IAM-REVOKE-01 — invariant A (émission) : une identité absente, suspendue ou désactivée
 * n'obtient plus aucun token, ni par le login ORGANIZATION ni par le login SPACE transitoire,
 * et le refus ne se distingue pas d'un mauvais mot de passe.
 * <p>
 * Chaîne réelle de bout en bout sur PostgreSQL : inscription, création d'un employé, puis
 * changement du statut de son identité en base (aucune API ne l'écrit encore : récit de
 * gouvernance séparé).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class IdentityRevocationLoginPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID OTHER_ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000a7");
    private static final UUID OTHER_SPACE = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000a7");
    private static final UUID OTHER_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000a7");
    private static final UUID OTHER_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000a7");
    private static final String OTHER_ORG_CODE = "revoke-other-org";

    private static final String ORG_CODE = "revoke-org";
    private static final String SPACE_CODE = "revoke-space";
    private static final String FOUNDER_EMAIL = "founder@revoke.test";
    private static final String FOUNDER_PASSWORD = "Founder!Pass1";
    private static final String EMPLOYEE_EMAIL = "employee@revoke.test";
    private static final String EMPLOYEE_PASSWORD = "Employee!Pass1";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;

    private TasHttpFixture fixture;
    private UUID organizationId;
    private UUID founderAccountId;
    private UUID employeeAccountId;

    @BeforeEach
    void organizationWithAFounderAndAnEmployee() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE, OTHER_ORG_CODE);
        fixture.organizationWithSpaceAndAdmin(OTHER_ORG, OTHER_ORG_CODE, OTHER_SPACE, "revoke-other-space",
                OTHER_ACCOUNT, OTHER_USER);

        HttpResponse<String> signup = fixture.postJson("/api/v1/orgs/signup",
                fixture.organizationToken(OTHER_ORG, OTHER_ACCOUNT), """
                {"organization":{"code":"%s","name":"Revoke"},
                 "space":{"code":"%s","name":"Revoke Space"},
                 "account":{"email":"%s","password":"%s"},
                 "profile":{"username":"revoke-founder","firstName":"Re","lastName":"Voke"}}"""
                        .formatted(ORG_CODE, SPACE_CODE, FOUNDER_EMAIL, FOUNDER_PASSWORD));
        assertThat(signup.statusCode()).as("inscription : %s", signup.body()).isEqualTo(201);
        organizationId = UUID.fromString(TasHttpFixture.body(signup).path("organizationId").asText());
        founderAccountId = accountId(FOUNDER_EMAIL);

        HttpResponse<String> employee = fixture.postJson(
                "/api/v1/orgs/" + ORG_CODE + "/spaces/" + SPACE_CODE + "/users",
                tokenOf(login(FOUNDER_EMAIL, FOUNDER_PASSWORD, SPACE_CODE)), """
                {"email":"%s","rawPassword":"%s","username":"revoke-employee","firstName":"Emp","lastName":"Loyee"}"""
                        .formatted(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD));
        assertThat(employee.statusCode()).as("création de l'employé : %s", employee.body()).isEqualTo(201);
        employeeAccountId = accountId(EMPLOYEE_EMAIL);
    }

    @AfterEach
    void cleanUp() {
        fixture.purgeOrganizations(ORG_CODE, OTHER_ORG_CODE);
    }

    @Test
    void the_founder_and_the_employee_each_have_exactly_one_active_human_identity() {
        assertThat(identities(founderAccountId)).isEqualTo(1L);
        assertThat(identities(employeeAccountId)).isEqualTo(1L);
        assertThat(status(founderAccountId)).isEqualTo("ACTIVE");
        assertThat(status(employeeAccountId)).isEqualTo("ACTIVE");
    }

    @Test
    void an_active_identity_still_logs_in_on_both_paths() {
        assertThat(login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD, null).statusCode()).isEqualTo(200);
        assertThat(login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD, SPACE_CODE).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "DISABLED"})
    void a_revoked_identity_obtains_no_token_on_either_path(String revoked) {
        setStatus(employeeAccountId, revoked);

        assertRefusedLikeABadPassword(login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD, null), null);
        assertRefusedLikeABadPassword(login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD, SPACE_CODE), SPACE_CODE);
    }

    @Test
    void an_absent_identity_obtains_no_token_on_either_path() {
        jdbc.update("DELETE FROM takibo_identities WHERE org_id = ? AND account_id = ?",
                organizationId, employeeAccountId);

        assertRefusedLikeABadPassword(login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD, null), null);
        assertRefusedLikeABadPassword(login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD, SPACE_CODE), SPACE_CODE);
    }

    @Test
    void a_reactivated_identity_logs_in_again() {
        setStatus(employeeAccountId, "DISABLED");
        assertThat(login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD, null).statusCode()).isEqualTo(401);

        setStatus(employeeAccountId, "ACTIVE");

        assertThat(login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD, null).statusCode()).isEqualTo(200);
    }

    @Test
    void refusing_a_revoked_identity_does_not_count_a_failure() {
        setStatus(employeeAccountId, "SUSPENDED");

        login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD, null);

        assertThat(jdbc.queryForObject(
                "SELECT failed_attempts FROM account_credentials WHERE org_id = ? AND account_id = ?",
                Integer.class, organizationId, employeeAccountId)).isZero();
    }

    @Test
    void revoking_the_employee_leaves_the_founder_untouched() {
        setStatus(employeeAccountId, "DISABLED");

        assertThat(login(FOUNDER_EMAIL, FOUNDER_PASSWORD, null).statusCode()).isEqualTo(200);
    }

    // ---------- Outils ----------

    /** Même statut et même contenu public qu'un mauvais mot de passe : rien ne révèle la cause. */
    private void assertRefusedLikeABadPassword(HttpResponse<String> refused, String spaceCode) {
        HttpResponse<String> badPassword = login(FOUNDER_EMAIL, "Wrong!Pass1", spaceCode);
        assertThat(refused.statusCode()).as("réponse : %s", refused.body()).isEqualTo(401);
        assertThat(publicPart(refused)).isEqualTo(publicPart(badPassword));
        assertThat(refused.body()).doesNotContain("accessToken");
    }

    private static String publicPart(HttpResponse<String> response) {
        JsonNode body = TasHttpFixture.body(response);
        return body.path("status").asText() + "|" + body.path("code").asText() + "|"
                + body.path("message").asText() + "|" + body.path("error").asText();
    }

    private HttpResponse<String> login(String email, String password, String spaceCode) {
        String space = spaceCode == null ? "" : ",\"spaceCode\":\"" + spaceCode + "\"";
        return fixture.postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s","orgCode":"%s"%s}""".formatted(email, password, ORG_CODE, space));
    }

    private static String tokenOf(HttpResponse<String> response) {
        assertThat(response.statusCode()).as("connexion : %s", response.body()).isEqualTo(200);
        return TasHttpFixture.body(response).path("accessToken").asText();
    }

    private UUID accountId(String email) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE org_id = ? AND email = ?",
                UUID.class, organizationId, email);
    }

    private long identities(UUID accountId) {
        return fixture.count("SELECT COUNT(*) FROM takibo_identities WHERE org_id = ? AND account_id = ?",
                organizationId, accountId);
    }

    private String status(UUID accountId) {
        return jdbc.queryForObject("SELECT identity_status FROM takibo_identities WHERE org_id = ? AND account_id = ?",
                String.class, organizationId, accountId);
    }

    private void setStatus(UUID accountId, String status) {
        assertThat(jdbc.update("UPDATE takibo_identities SET identity_status = ? WHERE org_id = ? AND account_id = ?",
                status, organizationId, accountId)).isEqualTo(1);
    }
}
