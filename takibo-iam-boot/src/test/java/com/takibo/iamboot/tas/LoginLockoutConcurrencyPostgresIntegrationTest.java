package com.takibo.iamboot.tas;

import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.domain.model.FailedAttemptReservation;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.security.SpringPasswordHasherCase;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;

/**
 * SEC-HUNT-02 — le seuil de verrouillage tient sous des échecs concurrents.
 * <p>
 * Seuil : 5 échecs (défaut), verrou de 2 secondes pour éprouver l'expiration. Chaîne réelle de
 * bout en bout sur PostgreSQL. Le mot de passe réel n'est jamais vérifié plus de 5 fois avant le
 * verrou : l'espion compte chaque comparaison contre le hash stocké de la victime.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4",
                "takibo.auth.login.max-failed-attempts=5",
                "takibo.auth.login.lock-seconds=2"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class LoginLockoutConcurrencyPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID OTHER_ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000b2");
    private static final UUID OTHER_SPACE = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000b2");
    private static final UUID OTHER_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000b2");
    private static final UUID OTHER_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000b2");
    private static final String OTHER_ORG_CODE = "hunt02-bootstrap-org";

    private static final String ORG_CODE = "hunt02-lockout-org";
    private static final String SPACE_CODE = "hunt02-space";
    private static final String FOUNDER_EMAIL = "founder@hunt02.test";
    private static final String VICTIM_EMAIL = "victim@hunt02.test";
    private static final String PASSWORD = "Lockout!Pass1";
    private static final String WRONG = "Wrong!Pass1";
    private static final int MAX_ATTEMPTS = 5;
    private static final int BURST = 40;

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;
    @MockitoSpyBean private SpringPasswordHasherCase passwordHasher;
    @Autowired private AccountCredentialsRepository credentials;

    private TasHttpFixture fixture;
    private UUID organizationId;
    private UUID victimAccountId;

    @BeforeEach
    void organizationWithAVictim() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE, OTHER_ORG_CODE);
        fixture.organizationWithSpaceAndAdmin(OTHER_ORG, OTHER_ORG_CODE, OTHER_SPACE, "hunt02-bootstrap-space",
                OTHER_ACCOUNT, OTHER_USER);

        HttpResponse<String> signup = fixture.postJson("/api/v1/orgs/signup",
                fixture.organizationToken(OTHER_ORG, OTHER_ACCOUNT), """
                {"organization":{"code":"%s","name":"Hunt 02"},
                 "space":{"code":"%s","name":"Hunt 02 Space"},
                 "account":{"email":"%s","password":"%s"},
                 "profile":{"username":"hunt02-founder","firstName":"Hu","lastName":"Nt"}}"""
                        .formatted(ORG_CODE, SPACE_CODE, FOUNDER_EMAIL, PASSWORD));
        assertThat(signup.statusCode()).as("inscription : %s", signup.body()).isEqualTo(201);
        organizationId = UUID.fromString(TasHttpFixture.body(signup).path("organizationId").asText());

        HttpResponse<String> founder = login(FOUNDER_EMAIL, PASSWORD);
        assertThat(founder.statusCode()).isEqualTo(200);
        HttpResponse<String> victim = fixture.postJson("/api/v1/orgs/" + ORG_CODE + "/spaces/" + SPACE_CODE + "/users",
                TasHttpFixture.body(founder).path("accessToken").asText(), """
                {"email":"%s","rawPassword":"%s","username":"hunt02-victim","firstName":"Vi","lastName":"Ctim"}"""
                        .formatted(VICTIM_EMAIL, PASSWORD));
        assertThat(victim.statusCode()).as("création de la victime : %s", victim.body()).isEqualTo(201);
        victimAccountId = jdbc.queryForObject("SELECT id FROM accounts WHERE org_id = ? AND email = ?",
                UUID.class, organizationId, VICTIM_EMAIL);
        clearInvocations(passwordHasher);
    }

    @AfterEach
    void cleanUp() {
        fixture.purgeOrganizations(ORG_CODE, OTHER_ORG_CODE);
    }

    // ---------- Comportement séquentiel : inchangé ----------

    @Test
    void the_threshold_is_exact_sequentially() {
        for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
            assertThat(login(VICTIM_EMAIL, WRONG).statusCode()).isEqualTo(401);
        }
        assertThat(failedAttempts()).isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(isLocked()).as("pas de verrou avant le seuil").isFalse();

        assertThat(login(VICTIM_EMAIL, WRONG).statusCode()).isEqualTo(401);

        assertThat(failedAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(isLocked()).as("verrou au 5e échec").isTrue();
        assertThat(login(VICTIM_EMAIL, PASSWORD).statusCode()).as("bon mot de passe pendant le verrou").isEqualTo(401);
    }

    @Test
    void a_success_before_the_threshold_still_resets_the_counter() {
        for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
            login(VICTIM_EMAIL, WRONG);
        }

        assertThat(login(VICTIM_EMAIL, PASSWORD).statusCode()).isEqualTo(200);

        assertThat(failedAttempts()).isZero();
        assertThat(isLocked()).isFalse();
    }

    @Test
    void the_lock_expires_and_the_right_password_then_logs_in() throws InterruptedException {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            login(VICTIM_EMAIL, WRONG);
        }
        assertThat(isLocked()).isTrue();

        Thread.sleep(2_500);

        assertThat(login(VICTIM_EMAIL, PASSWORD).statusCode()).as("après expiration du verrou").isEqualTo(200);
        assertThat(failedAttempts()).isZero();
    }

    // ---------- Rouge sur main : HUNT-02 ----------

    @Test
    void concurrent_failures_below_the_threshold_are_all_counted() throws Exception {
        List<Integer> statuses = burst(MAX_ATTEMPTS - 1, WRONG);

        assertThat(statuses).as("réponses").containsOnly(401);
        assertThat(failedAttempts()).as("aucune mise à jour perdue").isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(isLocked()).isFalse();
    }

    @Test
    void a_concurrent_burst_never_gets_more_password_checks_than_the_threshold() throws Exception {
        List<Integer> statuses = burst(BURST, WRONG);

        assertThat(statuses).as("aucune collision ne devient une erreur 500").containsOnly(401);
        assertThat(passwordChecksAgainstTheVictimHash())
                .as("vérifications du vrai hash pour %d essais concurrents", BURST)
                .isEqualTo(MAX_ATTEMPTS);
        assertThat(failedAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(isLocked()).isTrue();
        assertThat(login(VICTIM_EMAIL, PASSWORD).statusCode()).as("bon mot de passe après la rafale").isEqualTo(401);
    }

    // ---------- IAM-REVOKE-01 préservé ----------

    @Test
    void the_right_password_of_a_suspended_identity_leaves_the_counter_exactly_as_it_was() {
        login(VICTIM_EMAIL, WRONG);
        login(VICTIM_EMAIL, WRONG);
        jdbc.update("UPDATE takibo_identities SET identity_status = 'SUSPENDED' WHERE org_id = ? AND account_id = ?",
                organizationId, victimAccountId);

        assertThat(login(VICTIM_EMAIL, PASSWORD).statusCode()).isEqualTo(401);

        assertThat(failedAttempts()).isEqualTo(2);
        assertThat(isLocked()).isFalse();
    }

    /**
     * Entrelacement exact relevé en revue : une tentative juste (identité révoquée) réserve le
     * 4e échec, une tentative fausse concurrente réserve le 5e et pose le verrou, puis la première
     * est annulée. Il reste 4 vrais échecs : le compte ne doit pas rester verrouillé.
     */
    @Test
    void cancelling_a_reservation_unlocks_an_account_locked_by_a_concurrent_failure_below_the_threshold() {
        jdbc.update("UPDATE account_credentials SET failed_attempts = 3 WHERE org_id = ? AND account_id = ?",
                organizationId, victimAccountId);
        Instant now = Instant.now();
        OrganizationId org = new OrganizationId(organizationId);
        AccountId account = AccountId.of(victimAccountId);

        FailedAttemptReservation rightPassword = credentials.reserveFailedAttempt(org, account, MAX_ATTEMPTS,
                now.plusSeconds(2), now).orElseThrow();
        FailedAttemptReservation concurrentFailure = credentials.reserveFailedAttempt(org, account, MAX_ATTEMPTS,
                now.plusSeconds(2), now).orElseThrow();
        assertThat(concurrentFailure.locksAccount()).as("le 5e échec pose le verrou").isTrue();

        credentials.cancelFailedAttempt(org, account, rightPassword, MAX_ATTEMPTS, now);

        assertThat(failedAttempts()).isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(isLocked()).as("4 vrais échecs : aucun verrou").isFalse();
    }

    @Test
    void cancelling_the_reservation_that_relocked_an_expired_account_lifts_that_lock() {
        jdbc.update("UPDATE account_credentials SET failed_attempts = 5, locked_until = now() - interval '1 second'"
                + " WHERE org_id = ? AND account_id = ?", organizationId, victimAccountId);
        Instant now = Instant.now();
        OrganizationId org = new OrganizationId(organizationId);
        AccountId account = AccountId.of(victimAccountId);

        FailedAttemptReservation reservation = credentials.reserveFailedAttempt(org, account, MAX_ATTEMPTS,
                now.plusSeconds(2), now).orElseThrow();
        assertThat(reservation.locksAccount()).isTrue();

        credentials.cancelFailedAttempt(org, account, reservation, MAX_ATTEMPTS, now);

        assertThat(failedAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(isLocked()).isFalse();
    }

    // ---------- Outils ----------

    private List<Integer> burst(int size, String password) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(size);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Callable<Integer>> attempts = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                attempts.add(() -> {
                    start.await();
                    return login(VICTIM_EMAIL, password).statusCode();
                });
            }
            List<Future<Integer>> futures = new ArrayList<>();
            attempts.forEach(attempt -> futures.add(pool.submit(attempt)));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private long passwordChecksAgainstTheVictimHash() {
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM account_credentials WHERE org_id = ? AND account_id = ?",
                String.class, organizationId, victimAccountId);
        return mockingDetails(passwordHasher).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("matches"))
                .filter(invocation -> hash.equals(invocation.getArgument(1)))
                .count();
    }

    private int failedAttempts() {
        return jdbc.queryForObject("SELECT failed_attempts FROM account_credentials WHERE org_id = ? AND account_id = ?",
                Integer.class, organizationId, victimAccountId);
    }

    private boolean isLocked() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT locked_until IS NOT NULL AND locked_until > now() FROM account_credentials"
                        + " WHERE org_id = ? AND account_id = ?",
                Boolean.class, organizationId, victimAccountId));
    }

    private HttpResponse<String> login(String email, String password) {
        return fixture.postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s","orgCode":"%s","spaceCode":"%s"}"""
                .formatted(email, password, ORG_CODE, SPACE_CODE));
    }
}
