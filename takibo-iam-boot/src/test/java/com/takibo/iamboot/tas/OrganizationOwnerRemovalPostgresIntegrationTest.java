package com.takibo.iamboot.tas;

import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 : un administrateur d'organisation ne peut pas retirer le rôle de propriétaire, et une
 * organisation ne peut jamais perdre son dernier propriétaire.
 * <p>
 * Parcours réel de bout en bout sur PostgreSQL, sans aucune donnée injectée dans les tables RBAC :
 * inscription (le fondateur reçoit R_ORG_OWNER), connexion du fondateur, création d'un employé,
 * attribution de R_ORG_ADMIN par le fondateur, connexion de l'employé, puis tentative de retrait de
 * R_ORG_OWNER au fondateur avec le token de l'employé.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class OrganizationOwnerRemovalPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID BOOTSTRAP_ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000a9");
    private static final UUID BOOTSTRAP_SPACE = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000a9");
    private static final UUID BOOTSTRAP_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000a9");
    private static final UUID BOOTSTRAP_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000a9");
    private static final String BOOTSTRAP_CODE = "p0-bootstrap-org";

    private static final String ORG_CODE = "p0-owner-org";
    private static final String SPACE_CODE = "p0-space";
    private static final String FOUNDER_EMAIL = "founder@p0.test";
    private static final String FOUNDER_PASSWORD = "Founder!Pass1";
    private static final String ADMIN_EMAIL = "org-admin@p0.test";
    private static final String ADMIN_PASSWORD = "OrgAdmin!Pass1";
    private static final String USERS = "/api/v1/orgs/" + ORG_CODE + "/spaces/" + SPACE_CODE + "/users";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;
    @Autowired private JwtDecoder jwtDecoder;

    private TasHttpFixture fixture;
    private UUID organizationId;
    private UUID founderUserId;
    private String orgAdminToken;

    @BeforeEach
    void provisionOrganizationWithOwnerAndOrgAdmin() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE, BOOTSTRAP_CODE);
        fixture.organizationWithSpaceAndAdmin(BOOTSTRAP_ORG, BOOTSTRAP_CODE, BOOTSTRAP_SPACE, "p0-bootstrap-space",
                BOOTSTRAP_ACCOUNT, BOOTSTRAP_USER);

        HttpResponse<String> signup = fixture.postJson("/api/v1/orgs/signup",
                fixture.organizationToken(BOOTSTRAP_ORG, BOOTSTRAP_ACCOUNT), """
                {"organization":{"code":"%s","name":"P0 Owner"},
                 "space":{"code":"%s","name":"P0 Space"},
                 "account":{"email":"%s","password":"%s"},
                 "profile":{"username":"p0-founder","firstName":"P0","lastName":"Founder"}}"""
                        .formatted(ORG_CODE, SPACE_CODE, FOUNDER_EMAIL, FOUNDER_PASSWORD));
        assertThat(signup.statusCode()).as("inscription : %s", signup.body()).isEqualTo(201);
        organizationId = UUID.fromString(TasHttpFixture.body(signup).path("organizationId").asText());
        founderUserId = UUID.fromString(TasHttpFixture.body(signup).path("userId").asText());
        assertThat(ownerAssignments()).as("le fondateur est propriétaire").isEqualTo(1L);

        String founderToken = login(FOUNDER_EMAIL, FOUNDER_PASSWORD);

        HttpResponse<String> employee = fixture.postJson(USERS, founderToken, """
                {"email":"%s","rawPassword":"%s","username":"p0-org-admin","firstName":"Org","lastName":"Admin"}"""
                .formatted(ADMIN_EMAIL, ADMIN_PASSWORD));
        assertThat(employee.statusCode()).as("création de l'employé : %s", employee.body()).isEqualTo(201);
        String employeeUserId = TasHttpFixture.body(employee).path("id").asText();

        HttpResponse<String> delegation = fixture.postJson(USERS + "/" + employeeUserId + "/roles", founderToken,
                "{\"roleCode\":\"R_ORG_ADMIN\",\"reason\":\"P0 delegation\"}");
        assertThat(delegation.statusCode()).as("attribution de R_ORG_ADMIN : %s", delegation.body()).isEqualTo(200);

        orgAdminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    @AfterEach
    void cleanUp() {
        fixture.purgeOrganizations(ORG_CODE, BOOTSTRAP_CODE);
    }

    @Test
    void given_an_org_admin_when_removing_the_last_owner_role_then_it_is_refused_and_the_organization_keeps_its_owner() {
        HttpResponse<String> removal = fixture.send("DELETE", USERS + "/" + founderUserId + "/roles/R_ORG_OWNER",
                orgAdminToken, Map.of(), null);

        assertThat(removal.statusCode())
                .as("retrait de R_ORG_OWNER par un R_ORG_ADMIN, réponse brute : %s", removal.body())
                .isIn(403, 409);
        assertThat(ownerAssignments())
                .as("propriétaires restants après la tentative (réponse : %d %s)", removal.statusCode(), removal.body())
                .isEqualTo(1L);
    }

    @Test
    void given_an_org_admin_when_removing_the_owner_role_then_the_founder_still_logs_in_as_owner() {
        fixture.send("DELETE", USERS + "/" + founderUserId + "/roles/R_ORG_OWNER", orgAdminToken, Map.of(), null);

        HttpResponse<String> founderLogin = fixture.postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s","orgCode":"%s"}""".formatted(FOUNDER_EMAIL, FOUNDER_PASSWORD, ORG_CODE));

        assertThat(founderLogin.statusCode()).as("connexion du fondateur : %s", founderLogin.body()).isEqualTo(200);
        var roles = jwtDecoder.decode(TasHttpFixture.body(founderLogin).path("accessToken").asText())
                .getClaimAsStringList("roles");
        assertThat(roles).as("rôles signés dans le token du fondateur après la tentative").contains("R_ORG_OWNER");
    }

    private long ownerAssignments() {
        return fixture.count("""
                SELECT COUNT(*) FROM role_assignments
                WHERE org_id = ? AND space_id IS NULL AND role_code = 'R_ORG_OWNER'""", organizationId);
    }

    private String login(String email, String password) {
        HttpResponse<String> response = fixture.postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s","orgCode":"%s","spaceCode":"%s"}"""
                .formatted(email, password, ORG_CODE, SPACE_CODE));
        assertThat(response.statusCode()).as("connexion de %s : %s", email, response.body()).isEqualTo(200);
        return TasHttpFixture.body(response).path("accessToken").asText();
    }
}
