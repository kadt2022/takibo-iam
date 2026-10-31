package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La politique de mot de passe s'applique sur les deux seuls parcours qui créent un mot de passe :
 * l'inscription d'une organisation et la création d'un utilisateur par la route lisible.
 * <p>
 * Chaîne réelle de bout en bout sur PostgreSQL. Chaque refus est un 400
 * {@code PASSWORD_POLICY_VIOLATION} qui n'écrit rien. Le témoin positif prouve que la route
 * accepte un mot de passe conforme, pour qu'un refus ne puisse pas venir d'une autre cause.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class PasswordPolicyHttpPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000f1");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000f1");
    private static final UUID ADMIN_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000f1");
    private static final UUID ADMIN_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000f1");

    private static final String ORG_CODE = "pwd-org";
    private static final String SPACE_CODE = "pwd-space";
    private static final String SIGNUP_ORG_CODE = "pwd-signup-org";
    private static final String USERS = "/api/v1/orgs/" + ORG_CODE + "/spaces/" + SPACE_CODE + "/users";
    private static final String NEW_EMAIL = "new-user@pwd.test";

    /** Limite de BCrypt : au-delà de 72 octets, le mot de passe ne peut pas être haché. */
    private static final String PASSWORD_72_BYTES = "Aa1!" + "a".repeat(68);
    private static final String PASSWORD_73_BYTES = "Aa1!" + "a".repeat(69);

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;

    private TasHttpFixture fixture;
    private String adminToken;

    @BeforeEach
    void seed() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE, SIGNUP_ORG_CODE);
        fixture.organizationWithSpaceAndAdmin(ORG_ID, ORG_CODE, SPACE_ID, SPACE_CODE, ADMIN_ACCOUNT, ADMIN_USER);
        adminToken = fixture.spaceToken(ORG_ID, SPACE_ID, ADMIN_ACCOUNT, ADMIN_USER,
                List.of(TechnicalPermission.SPACE_USERS_MANAGE.code()));
    }

    @AfterEach
    void cleanUp() {
        fixture.purgeOrganizations(ORG_CODE, SIGNUP_ORG_CODE);
    }

    // ---------- Création d'un utilisateur par la route lisible ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "Aa1!aaa",      // trop court
            "abcdefg1!",    // sans majuscule
            "ABCDEFG1!",    // sans minuscule
            "Abcdefgh!",    // sans chiffre
            "Abcdefgh1"     // sans caractère spécial
    })
    void given_a_password_outside_the_policy_when_creating_a_user_then_it_is_refused_and_nothing_is_written(
            String password) {
        assertPolicyViolation(createUser(password));
        assertNoUserCreated();
    }

    @Test
    void given_a_password_over_72_bytes_when_creating_a_user_then_it_is_refused_and_nothing_is_written() {
        assertThat(PASSWORD_73_BYTES.getBytes(StandardCharsets.UTF_8)).hasSize(73);

        assertPolicyViolation(createUser(PASSWORD_73_BYTES));
        assertNoUserCreated();
    }

    @Test
    void given_a_compliant_password_of_72_bytes_when_creating_a_user_then_the_user_is_created() {
        assertThat(PASSWORD_72_BYTES.getBytes(StandardCharsets.UTF_8)).hasSize(72);

        HttpResponse<String> response = createUser(PASSWORD_72_BYTES);

        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(201);
        assertThat(fixture.count("SELECT COUNT(*) FROM accounts WHERE org_id = ? AND email = ?", ORG_ID, NEW_EMAIL))
                .isEqualTo(1L);
    }

    // ---------- Inscription d'une organisation ----------

    @Test
    void given_a_password_outside_the_policy_when_signing_up_then_it_is_refused_and_nothing_is_written() {
        assertPolicyViolation(signup("Abcdefgh1"));
        assertNoSignupWritten();
    }

    @Test
    void given_a_password_over_72_bytes_when_signing_up_then_it_is_refused_and_nothing_is_written() {
        assertPolicyViolation(signup(PASSWORD_73_BYTES));
        assertNoSignupWritten();
    }

    @Test
    void given_a_compliant_password_when_signing_up_then_the_organization_is_created() {
        HttpResponse<String> response = signup(PASSWORD_72_BYTES);

        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(201);
        assertThat(fixture.count("SELECT COUNT(*) FROM organizations WHERE code = ?", SIGNUP_ORG_CODE)).isEqualTo(1L);
    }

    private HttpResponse<String> createUser(String password) {
        return fixture.postJson(USERS, adminToken, """
                {"email":"%s","rawPassword":"%s","username":"pwd-new","firstName":"P","lastName":"W"}"""
                .formatted(NEW_EMAIL, password));
    }

    private HttpResponse<String> signup(String password) {
        return fixture.postJson("/api/v1/orgs/signup", fixture.organizationToken(ORG_ID, ADMIN_ACCOUNT), """
                {"organization":{"code":"%s","name":"Pwd Signup"},
                 "space":{"code":"pwd-signup-space","name":"Signup Space"},
                 "account":{"email":"founder@pwd.test","password":"%s"},
                 "profile":{"username":"pwd-founder","firstName":"P","lastName":"Founder"}}"""
                .formatted(SIGNUP_ORG_CODE, password));
    }

    private static void assertPolicyViolation(HttpResponse<String> response) {
        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(400);
        JsonNode body = TasHttpFixture.body(response);
        assertThat(body.path("code").asText()).as("réponse brute : %s", response.body())
                .isEqualTo("PASSWORD_POLICY_VIOLATION");
    }

    private void assertNoUserCreated() {
        assertThat(fixture.count("SELECT COUNT(*) FROM accounts WHERE email = ?", NEW_EMAIL)).isZero();
        assertThat(fixture.count("SELECT COUNT(*) FROM users WHERE space_id = ?", SPACE_ID)).isEqualTo(1L);
    }

    private void assertNoSignupWritten() {
        assertThat(fixture.count("SELECT COUNT(*) FROM organizations WHERE code = ?", SIGNUP_ORG_CODE)).isZero();
        assertThat(fixture.count("SELECT COUNT(*) FROM accounts WHERE email = ?", "founder@pwd.test")).isZero();
    }
}
