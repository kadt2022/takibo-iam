package com.takibo.iamboot.tas;

import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IAM-REVOKE-01 — la migration de rattrapage crée les identités manquantes et ne touche à
 * aucune identité existante : une identité suspendue ou désactivée n'est jamais réactivée.
 * <p>
 * Flyway l'a déjà jouée au démarrage, sur une base sans ces données : le test rejoue le fichier
 * versionné lui-même contre un état préparé, ce qui prouve aussi son idempotence.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class TakiboIdentityBackfillMigrationPostgresIntegrationTest extends TasPostgresBaseline {

    private static final String MIGRATION = "db/migration/V202611100001__core__takibo_identities_backfill.sql";
    private static final UUID ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000b8");
    private static final UUID OTHER_ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000b9");
    private static final UUID WITHOUT_IDENTITY = UUID.fromString("cccccccc-0000-0000-0000-0000000000b1");
    private static final UUID SUSPENDED = UUID.fromString("cccccccc-0000-0000-0000-0000000000b2");
    private static final UUID DISABLED = UUID.fromString("cccccccc-0000-0000-0000-0000000000b3");
    private static final UUID OTHER_ORG_WITHOUT_IDENTITY = UUID.fromString("cccccccc-0000-0000-0000-0000000000b4");

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;

    private TasHttpFixture fixture;

    @BeforeEach
    void accountsWithAndWithoutIdentity() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations("backfill-org", "backfill-other");
        organization(ORG, "backfill-org");
        organization(OTHER_ORG, "backfill-other");
        account(WITHOUT_IDENTITY, ORG, "without@backfill.test");
        account(SUSPENDED, ORG, "suspended@backfill.test");
        identity(SUSPENDED, ORG, "SUSPENDED");
        account(DISABLED, ORG, "disabled@backfill.test");
        identity(DISABLED, ORG, "DISABLED");
        account(OTHER_ORG_WITHOUT_IDENTITY, OTHER_ORG, "without@backfill.test");
    }

    @AfterEach
    void cleanUp() {
        fixture.purgeOrganizations("backfill-org", "backfill-other");
    }

    @Test
    void a_missing_identity_is_created_active_and_human_in_the_organization_of_its_account() throws IOException {
        runMigration();

        Map<String, Object> created = identityOf(WITHOUT_IDENTITY);
        assertThat(created).containsEntry("org_id", ORG)
                .containsEntry("identity_id", WITHOUT_IDENTITY)
                .containsEntry("identity_type", "HUMAN")
                .containsEntry("identity_status", "ACTIVE");
        assertThat(identityOf(OTHER_ORG_WITHOUT_IDENTITY)).containsEntry("org_id", OTHER_ORG);
    }

    @Test
    void a_suspended_or_disabled_identity_is_never_reactivated() throws IOException {
        runMigration();

        assertThat(identityOf(SUSPENDED)).containsEntry("identity_status", "SUSPENDED");
        assertThat(identityOf(DISABLED)).containsEntry("identity_status", "DISABLED");
    }

    @Test
    void replaying_the_migration_changes_nothing() throws IOException {
        runMigration();
        long afterFirstRun = fixture.count("SELECT COUNT(*) FROM takibo_identities WHERE org_id IN (?, ?)", ORG, OTHER_ORG);

        runMigration();

        assertThat(fixture.count("SELECT COUNT(*) FROM takibo_identities WHERE org_id IN (?, ?)", ORG, OTHER_ORG))
                .isEqualTo(afterFirstRun).isEqualTo(4L);
        assertThat(identityOf(SUSPENDED)).containsEntry("identity_status", "SUSPENDED");
    }

    // ---------- Outils ----------

    private void runMigration() throws IOException {
        jdbc.execute(new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8));
    }

    private Map<String, Object> identityOf(UUID accountId) {
        return jdbc.queryForMap("""
                SELECT identity_id, org_id, identity_type, identity_status
                FROM takibo_identities WHERE account_id = ?""", accountId);
    }

    private void organization(UUID id, String code) {
        jdbc.update("INSERT INTO organizations (id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')", id, code, code);
    }

    private void account(UUID id, UUID orgId, String email) {
        jdbc.update("INSERT INTO accounts (id, org_id, email) VALUES (?, ?, ?)", id, orgId, email);
    }

    private void identity(UUID accountId, UUID orgId, String status) {
        jdbc.update("""
                INSERT INTO takibo_identities (identity_id, org_id, account_id, identity_type, identity_status)
                VALUES (?, ?, ?, 'HUMAN', ?)""", accountId, orgId, accountId, status);
    }
}
