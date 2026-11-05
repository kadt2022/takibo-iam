package com.takibo.iamboot.tas;

import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une attribution TECHNICAL dont le code est absent du catalogue ne confère aucun pouvoir :
 * elle n'entre pas dans le token, et un rôle hors catalogue présent dans un token déjà signé
 * n'est pas retenu par TSM.
 * <p>
 * Chaîne réelle de bout en bout sur PostgreSQL : inscription, création d'un employé sans rôle,
 * donnée d'attribution héritée insérée en base, connexion, puis accès à des ressources réservées.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class UnknownTechnicalRoleTokenPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID OTHER_ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000e7");
    private static final UUID OTHER_SPACE = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000e7");
    private static final UUID OTHER_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000e7");
    private static final UUID OTHER_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000e7");
    private static final String OTHER_ORG_CODE = "rbac-other-org";
    private static final String OTHER_SPACE_CODE = "rbac-other-space";

    private static final String ORG_CODE = "rbac-role-org";
    private static final String SPACE_CODE = "rbac-space";
    private static final String FOUNDER_EMAIL = "founder@rbac.test";
    private static final String FOUNDER_PASSWORD = "Founder!Pass1";
    private static final String EMPLOYEE_EMAIL = "employee@rbac.test";
    private static final String EMPLOYEE_PASSWORD = "Employee!Pass1";
    private static final String USERS = "/api/v1/orgs/" + ORG_CODE + "/spaces/" + SPACE_CODE + "/users";
    private static final String PLATFORM_FORM = "ROLE_R_TAKIBO_PLATFORM_ADMIN";
    private static final String ORG_ADMIN_FORM = "ROLE_R_ORG_ADMIN";
    private static final String GROUP_FORM = "ROLE_G_ORG_ADMINS";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;
    @Autowired private JwtDecoder jwtDecoder;

    private TasHttpFixture fixture;
    private UUID organizationId;
    private UUID spaceId;
    private UUID employeeUserId;
    private UUID employeeAccountId;

    @BeforeEach
    void organizationWithAnEmployeeWithoutRole() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE, OTHER_ORG_CODE);
        fixture.organizationWithSpaceAndAdmin(OTHER_ORG, OTHER_ORG_CODE, OTHER_SPACE, OTHER_SPACE_CODE,
                OTHER_ACCOUNT, OTHER_USER);

        HttpResponse<String> signup = fixture.postJson("/api/v1/orgs/signup",
                fixture.organizationToken(OTHER_ORG, OTHER_ACCOUNT), """
                {"organization":{"code":"%s","name":"RBAC"},
                 "space":{"code":"%s","name":"RBAC Space"},
                 "account":{"email":"%s","password":"%s"},
                 "profile":{"username":"rbac-founder","firstName":"Rbac","lastName":"Founder"}}"""
                        .formatted(ORG_CODE, SPACE_CODE, FOUNDER_EMAIL, FOUNDER_PASSWORD));
        assertThat(signup.statusCode()).as("inscription : %s", signup.body()).isEqualTo(201);
        organizationId = UUID.fromString(TasHttpFixture.body(signup).path("organizationId").asText());
        spaceId = jdbc.queryForObject("SELECT id FROM spaces WHERE org_id = ? AND code = ?", UUID.class,
                organizationId, SPACE_CODE);

        HttpResponse<String> employee = fixture.postJson(USERS, login(FOUNDER_EMAIL, FOUNDER_PASSWORD), """
                {"email":"%s","rawPassword":"%s","username":"rbac-employee","firstName":"Emp","lastName":"Loyee"}"""
                .formatted(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD));
        assertThat(employee.statusCode()).as("création de l'employé : %s", employee.body()).isEqualTo(201);
        employeeUserId = UUID.fromString(TasHttpFixture.body(employee).path("id").asText());
        employeeAccountId = jdbc.queryForObject("SELECT account_id FROM users WHERE id = ?", UUID.class,
                employeeUserId);
    }

    @AfterEach
    void cleanUp() {
        fixture.purgeOrganizations(ORG_CODE, OTHER_ORG_CODE);
    }

    // ---------- Émission ----------

    @Test
    void an_employee_without_role_has_no_role_and_no_platform_access() {
        String token = login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD);

        assertThat(claim(token, "roles")).isEmpty();
        assertThat(actuatorBeans(token).statusCode()).isEqualTo(403);
    }

    @Test
    void an_unknown_technical_role_never_enters_the_token_nor_grants_platform_access() {
        insertTechnicalRole(PLATFORM_FORM);
        insertTechnicalRole(ORG_ADMIN_FORM);
        String token = login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD);

        assertThat(claim(token, "roles")).as("rôles signés").isEmpty();
        assertThat(claim(token, "permissions")).as("permissions signées").isEmpty();
        assertThat(actuatorBeans(token).statusCode()).as("actuator").isEqualTo(403);
    }

    @Test
    void an_unknown_technical_group_never_enters_the_token() {
        insertTechnicalGroup(GROUP_FORM);
        String token = login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD);

        assertThat(claim(token, "groups")).as("groupes signés").isEmpty();
        assertThat(claim(token, "roles")).as("rôles signés").isEmpty();
    }

    @Test
    void a_known_technical_role_still_enters_the_token_with_its_permissions() {
        insertTechnicalRole("R_SPACE_AUDITOR");
        insertTechnicalRole(PLATFORM_FORM);
        String token = login(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD);

        assertThat(claim(token, "roles")).containsExactly("R_SPACE_AUDITOR");
        assertThat(claim(token, "permissions")).contains("P_SPACE_AUDIT_READ");
        assertThat(actuatorBeans(token).statusCode()).isEqualTo(403);
    }

    // ---------- Tokens déjà signés ----------

    @Test
    void a_space_token_signed_with_out_of_catalog_roles_grants_no_platform_access() {
        String token = tokenIssuer.issue(HumanTokenRequest.spaceScoped(organizationId, spaceId, employeeAccountId,
                employeeUserId, List.of(PLATFORM_FORM, ORG_ADMIN_FORM, "R_TAKIBO_PLATFORM_ADMIN"), List.of(),
                List.of())).accessToken();

        assertThat(claim(token, "roles")).as("le token porte bien les rôles hors catalogue")
                .contains(PLATFORM_FORM, ORG_ADMIN_FORM);
        assertThat(actuatorBeans(token).statusCode()).isEqualTo(403);
    }

    @Test
    void an_organization_token_signed_with_out_of_catalog_roles_grants_no_platform_access() {
        String token = tokenIssuer.issue(HumanTokenRequest.organizationScoped(organizationId, employeeAccountId,
                List.of(PLATFORM_FORM, "R_TAKIBO_PLATFORM_ADMIN"), List.of(), List.of())).accessToken();

        assertThat(actuatorBeans(token).statusCode()).isEqualTo(403);
    }

    @Test
    void out_of_catalog_roles_in_a_signed_token_do_not_open_another_organization() {
        String token = tokenIssuer.issue(HumanTokenRequest.spaceScoped(organizationId, spaceId, employeeAccountId,
                employeeUserId, List.of(PLATFORM_FORM, ORG_ADMIN_FORM), List.of(), List.of())).accessToken();

        HttpResponse<String> otherOrganization = fixture.send("GET",
                "/api/v1/orgs/" + OTHER_ORG_CODE + "/spaces/" + OTHER_SPACE_CODE + "/users", token, Map.of(), null);

        assertThat(otherOrganization.statusCode()).as("réponse : %s", otherOrganization.body()).isIn(403, 404);
    }

    // ---------- Outils ----------

    /** Forme des attributions héritées : source TECHNICAL, code libre. */
    private void insertTechnicalRole(String code) {
        jdbc.update("""
                INSERT INTO role_assignments (id, org_id, space_id, identity_type, identity_id, role_code, role_source)
                VALUES (?, ?, ?, 'ACCOUNT', ?, ?, 'TECHNICAL')
                """, UUID.randomUUID(), organizationId, spaceId, employeeAccountId, code);
    }

    private void insertTechnicalGroup(String code) {
        jdbc.update("""
                INSERT INTO group_assignments (id, org_id, space_id, identity_type, identity_id, group_code, group_source)
                VALUES (?, ?, ?, 'ACCOUNT', ?, ?, 'TECHNICAL')
                """, UUID.randomUUID(), organizationId, spaceId, employeeAccountId, code);
    }

    private HttpResponse<String> actuatorBeans(String token) {
        return fixture.send("GET", "/actuator/beans", token, Map.of(), null);
    }

    private List<String> claim(String token, String name) {
        Jwt jwt = jwtDecoder.decode(token);
        List<String> values = jwt.getClaimAsStringList(name);
        return values == null ? List.of() : values;
    }

    private String login(String email, String password) {
        HttpResponse<String> response = fixture.postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s","orgCode":"%s","spaceCode":"%s"}"""
                .formatted(email, password, ORG_CODE, SPACE_CODE));
        assertThat(response.statusCode()).as("connexion de %s : %s", email, response.body()).isEqualTo(200);
        return TasHttpFixture.body(response).path("accessToken").asText();
    }
}
