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
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-HUNT-06 — un Space garde toujours au moins un administrateur effectif actif.
 * <p>
 * Administrateur effectif : identité dont le User de ce Space est ACTIVE et qui détient
 * {@code R_SPACE_ADMIN}, directement ou par {@code G_SPACE_ADMINS}. Deux administrateurs qui se
 * retirent mutuellement ce pouvoir au même instant ne peuvent pas tous deux réussir.
 * <p>
 * Parcours réel sur PostgreSQL par l'API. Seule précondition posée en base : les pouvoirs
 * d'administration du fondateur dans ce Space sont retirés, pour que le Space n'ait que les
 * deux administrateurs du scénario (le fondateur ne peut pas se rétrograder lui-même par l'API).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class LastSpaceAdministratorConcurrencyPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID BOOTSTRAP_ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000b6");
    private static final UUID BOOTSTRAP_SPACE = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000b6");
    private static final UUID BOOTSTRAP_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000b6");
    private static final UUID BOOTSTRAP_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000b6");
    private static final String BOOTSTRAP_CODE = "hunt06-bootstrap-org";

    private static final String ORG_CODE = "hunt06-admins-org";
    private static final String SPACE_CODE = "hunt06-space";
    private static final String PASSWORD = "LastAdmin!Pass1";
    private static final String USERS = "/api/v1/orgs/" + ORG_CODE + "/spaces/" + SPACE_CODE + "/users";
    private static final int ROUNDS = 8;

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;

    private TasHttpFixture fixture;
    private UUID organizationId;
    private UUID spaceId;
    private UUID founderAccountId;
    private String founderToken;
    private String adminA;
    private String adminB;

    @BeforeEach
    void spaceWithTwoAdministrators() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE, BOOTSTRAP_CODE);
        fixture.organizationWithSpaceAndAdmin(BOOTSTRAP_ORG, BOOTSTRAP_CODE, BOOTSTRAP_SPACE, "hunt06-bootstrap-space",
                BOOTSTRAP_ACCOUNT, BOOTSTRAP_USER);
        HttpResponse<String> signup = fixture.postJson("/api/v1/orgs/signup",
                fixture.organizationToken(BOOTSTRAP_ORG, BOOTSTRAP_ACCOUNT), """
                {"organization":{"code":"%s","name":"Hunt 06"},
                 "space":{"code":"%s","name":"Hunt 06 Space"},
                 "account":{"email":"founder@hunt06.test","password":"%s"},
                 "profile":{"username":"hunt06-founder","firstName":"Hu","lastName":"Nt"}}"""
                        .formatted(ORG_CODE, SPACE_CODE, PASSWORD));
        assertThat(signup.statusCode()).as("inscription : %s", signup.body()).isEqualTo(201);
        organizationId = UUID.fromString(TasHttpFixture.body(signup).path("organizationId").asText());
        spaceId = UUID.fromString(TasHttpFixture.body(signup).path("spaceId").asText());
        founderAccountId = UUID.fromString(TasHttpFixture.body(signup).path("accountId").asText());
        founderToken = login("founder@hunt06.test");
        adminA = employee("admin-a");
        adminB = employee("admin-b");
    }

    @AfterEach
    void cleanUp() {
        fixture.purgeOrganizations(ORG_CODE, BOOTSTRAP_CODE);
    }

    // ---------- Rouge sur main : HUNT-06 ----------

    @Test
    void two_administrators_removing_each_others_direct_role_never_both_succeed() {
        grantRole(adminA);
        grantRole(adminB);
        withoutFounderAdministration();

        for (int round = 1; round <= ROUNDS; round++) {
            String tokenOfA = tokenOf("admin-a");
            String tokenOfB = tokenOf("admin-b");
            List<Integer> statuses = concurrently(
                    () -> removeRole(adminB, tokenOfA),
                    () -> removeRole(adminA, tokenOfB));

            assertThat(statuses).as("tour %d", round).containsExactlyInAnyOrder(200, 409);
            assertThat(effectiveAdministrators()).as("administrateurs effectifs après le tour %d", round).isEqualTo(1);
            grantRole(adminA);
            grantRole(adminB);
        }
    }

    @Test
    void two_administrators_removing_each_other_from_the_admin_group_never_both_succeed() {
        joinGroup(adminA);
        joinGroup(adminB);
        withoutFounderAdministration();

        for (int round = 1; round <= ROUNDS; round++) {
            String tokenOfA = tokenOf("admin-a");
            String tokenOfB = tokenOf("admin-b");
            List<Integer> statuses = concurrently(
                    () -> leaveGroup(adminB, tokenOfA),
                    () -> leaveGroup(adminA, tokenOfB));

            assertThat(statuses).as("tour %d", round).containsExactlyInAnyOrder(200, 409);
            assertThat(effectiveAdministrators()).as("administrateurs effectifs après le tour %d", round).isEqualTo(1);
            joinGroup(adminA);
            joinGroup(adminB);
        }
    }

    @Test
    void a_role_removal_and_a_group_removal_are_serialized_together() {
        grantRole(adminA);
        joinGroup(adminB);
        withoutFounderAdministration();

        for (int round = 1; round <= ROUNDS; round++) {
            String tokenOfA = tokenOf("admin-a");
            String tokenOfB = tokenOf("admin-b");
            List<Integer> statuses = concurrently(
                    () -> leaveGroup(adminB, tokenOfA),
                    () -> removeRole(adminA, tokenOfB));

            assertThat(statuses).as("tour %d", round).containsExactlyInAnyOrder(200, 409);
            assertThat(effectiveAdministrators()).as("après le tour %d", round).isEqualTo(1);
            grantRole(adminA);
            joinGroup(adminB);
        }
    }

    // ---------- L'invariant porte sur les administrateurs effectifs ----------

    @Test
    void the_last_effective_administrator_is_never_removed_and_nothing_is_deleted() {
        grantRole(adminA);
        grantRole(adminB);
        withoutFounderAdministration();
        assertThat(removeRole(adminB, tokenOf("admin-a"))).isEqualTo(200);
        long rowsBefore = administrationRows();

        int status = fixture.send("DELETE", USERS + "/" + adminA + "/roles/R_SPACE_ADMIN", founderToken, Map.of(), null)
                .statusCode();

        assertThat(status).as("le dernier administrateur effectif").isEqualTo(409);
        assertThat(administrationRows()).as("aucune suppression partielle").isEqualTo(rowsBefore);
        assertThat(effectiveAdministrators()).isEqualTo(1);
    }

    @Test
    void the_direct_role_of_an_administrator_who_stays_one_through_the_group_can_be_removed() {
        grantRole(adminA);
        joinGroup(adminA);
        withoutFounderAdministration();

        assertThat(fixture.send("DELETE", USERS + "/" + adminA + "/roles/R_SPACE_ADMIN", founderToken, Map.of(), null)
                .statusCode()).as("admin-a reste administrateur par G_SPACE_ADMINS").isEqualTo(200);
        assertThat(effectiveAdministrators()).isEqualTo(1);
    }

    @Test
    void the_last_direct_holder_can_lose_the_role_when_another_administrator_remains_through_the_group() {
        grantRole(adminA);
        joinGroup(adminB);
        withoutFounderAdministration();

        assertThat(removeRole(adminA, tokenOf("admin-b"))).as("admin-b reste par le groupe").isEqualTo(200);
        assertThat(effectiveAdministrators()).isEqualTo(1);
    }

    @Test
    void a_deactivated_administrator_does_not_count() {
        grantRole(adminA);
        grantRole(adminB);
        withoutFounderAdministration();
        String tokenOfB = tokenOf("admin-b");
        jdbc.update("UPDATE users SET status = 'DEACTIVATED' WHERE id = ?", UUID.fromString(adminB));

        assertThat(fixture.send("DELETE", USERS + "/" + adminA + "/roles/R_SPACE_ADMIN", tokenOfB, Map.of(), null)
                .statusCode()).as("seul administrateur actif : admin-a").isEqualTo(409);
    }

    // ---------- Cycle de vie : la même règle, sous le même verrou ----------

    @Test
    void deactivating_the_last_effective_administrator_is_refused_and_nothing_changes() {
        grantRole(adminA);
        withoutFounderAdministration();
        String before = stateOf(adminA);

        int status = restrict(adminA, founderToken);

        assertThat(status).as("désactiver le dernier administrateur effectif").isEqualTo(409);
        assertThat(stateOf(adminA)).as("statut et version inchangés").isEqualTo(before);
        assertThat(effectiveAdministrators()).isEqualTo(1);
    }

    @Test
    void deactivating_an_administrator_while_another_remains_is_allowed() {
        grantRole(adminA);
        grantRole(adminB);
        withoutFounderAdministration();

        assertThat(restrict(adminB, founderToken)).isEqualTo(200);
        assertThat(effectiveAdministrators()).isEqualTo(1);
    }

    @Test
    void deactivating_an_ordinary_user_is_never_blocked_by_the_rule() {
        withoutFounderAdministration();
        String ordinary = employee("ordinary");

        assertThat(effectiveAdministrators()).as("aucun administrateur effectif").isZero();
        assertThat(restrict(ordinary, founderToken)).isEqualTo(200);
    }

    @Test
    void two_administrators_deactivating_each_other_never_both_succeed() {
        grantRole(adminA);
        grantRole(adminB);
        withoutFounderAdministration();
        String tokenOfA = tokenOf("admin-a");
        String tokenOfB = tokenOf("admin-b");

        List<Integer> statuses = concurrently(
                () -> restrict(adminB, tokenOfA),
                () -> restrict(adminA, tokenOfB));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(effectiveAdministrators()).isEqualTo(1);
    }

    @Test
    void a_deactivation_and_a_role_removal_are_serialized_together() {
        grantRole(adminA);
        grantRole(adminB);
        withoutFounderAdministration();
        String tokenOfA = tokenOf("admin-a");
        String tokenOfB = tokenOf("admin-b");

        List<Integer> statuses = concurrently(
                () -> removeRole(adminB, tokenOfA),
                () -> restrict(adminA, tokenOfB));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(effectiveAdministrators()).isEqualTo(1);
    }

    // ---------- Outils ----------

    private List<Integer> concurrently(Supplier<Integer> first, Supplier<Integer> second) {
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Integer> one = CompletableFuture.supplyAsync(() -> awaitThen(start, first));
        CompletableFuture<Integer> two = CompletableFuture.supplyAsync(() -> awaitThen(start, second));
        start.countDown();
        return List.of(one.join(), two.join());
    }

    private static Integer awaitThen(CountDownLatch start, Supplier<Integer> call) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return call.get();
    }

    /** Précondition synthétique : le fondateur n'administre plus ce Space (il reste propriétaire). */
    private void withoutFounderAdministration() {
        jdbc.update("DELETE FROM role_assignments WHERE org_id = ? AND space_id = ? AND identity_id = ? AND role_code = 'R_SPACE_ADMIN'",
                organizationId, spaceId, founderAccountId);
        jdbc.update("DELETE FROM group_assignments WHERE org_id = ? AND space_id = ? AND identity_id = ? AND group_code = 'G_SPACE_ADMINS'",
                organizationId, spaceId, founderAccountId);
    }

    private long effectiveAdministrators() {
        return fixture.count("""
                SELECT COUNT(DISTINCT u.account_id) FROM users u
                WHERE u.org_id = ? AND u.space_id = ? AND u.status = 'ACTIVE'
                  AND (EXISTS (SELECT 1 FROM role_assignments r WHERE r.org_id = u.org_id AND r.space_id = u.space_id
                               AND r.identity_id = u.account_id AND r.role_code = 'R_SPACE_ADMIN')
                    OR EXISTS (SELECT 1 FROM group_assignments g WHERE g.org_id = u.org_id AND g.space_id = u.space_id
                               AND g.identity_id = u.account_id AND g.group_code = 'G_SPACE_ADMINS'))""",
                organizationId, spaceId);
    }

    private long administrationRows() {
        return fixture.count("SELECT COUNT(*) FROM role_assignments WHERE org_id = ? AND space_id = ? AND role_code = 'R_SPACE_ADMIN'",
                organizationId, spaceId)
                + fixture.count("SELECT COUNT(*) FROM group_assignments WHERE org_id = ? AND space_id = ? AND group_code = 'G_SPACE_ADMINS'",
                organizationId, spaceId);
    }

    private int restrict(String userId, String token) {
        return fixture.postJson(USERS + "/" + userId + "/deactivate", token, "{\"reason\":\"SEC-HUNT-06\"}")
                .statusCode();
    }

    private String stateOf(String userId) {
        return jdbc.queryForObject("SELECT status || '#' || version FROM users WHERE id = ?", String.class,
                UUID.fromString(userId));
    }

    private int removeRole(String userId, String token) {
        return fixture.send("DELETE", USERS + "/" + userId + "/roles/R_SPACE_ADMIN", token, Map.of(), null).statusCode();
    }

    private int leaveGroup(String userId, String token) {
        return fixture.send("DELETE", USERS + "/" + userId + "/groups/G_SPACE_ADMINS", token, Map.of(), null).statusCode();
    }

    private void grantRole(String userId) {
        HttpResponse<String> response = fixture.postJson(USERS + "/" + userId + "/roles", founderToken,
                "{\"roleCode\":\"R_SPACE_ADMIN\",\"reason\":\"SEC-HUNT-06\"}");
        assertThat(response.statusCode()).as("attribution : %s", response.body()).isEqualTo(200);
    }

    private void joinGroup(String userId) {
        HttpResponse<String> response = fixture.postJson(USERS + "/" + userId + "/groups", founderToken,
                "{\"groupCode\":\"G_SPACE_ADMINS\",\"reason\":\"SEC-HUNT-06\"}");
        assertThat(response.statusCode()).as("adhésion : %s", response.body()).isEqualTo(200);
    }

    private String employee(String name) {
        HttpResponse<String> created = fixture.postJson(USERS, founderToken, """
                {"email":"%s@hunt06.test","rawPassword":"%s","username":"hunt06-%s","firstName":"H","lastName":"U"}"""
                .formatted(name, PASSWORD, name));
        assertThat(created.statusCode()).as("création de %s : %s", name, created.body()).isEqualTo(201);
        return TasHttpFixture.body(created).path("id").asText();
    }

    private String tokenOf(String name) {
        return login(name + "@hunt06.test");
    }

    private String login(String email) {
        HttpResponse<String> response = fixture.postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s","orgCode":"%s","spaceCode":"%s"}"""
                .formatted(email, PASSWORD, ORG_CODE, SPACE_CODE));
        assertThat(response.statusCode()).as("connexion de %s : %s", email, response.body()).isEqualTo(200);
        return TasHttpFixture.body(response).path("accessToken").asText();
    }
}
