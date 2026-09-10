package com.takibo.iamboot.tas;

import com.takibo.authorizationserver.domain.authorization.port.AuthorizationRetentionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retention des autorisations OAuth 2.0 expirees (TAS-GRANTS-02B), sur PostgreSQL reel.
 * <p>
 * Le comportement teste ici ne peut pas etre prouve en memoire : il repose sur une colonne
 * generee, sur un index partiel, sur l'horloge du serveur et sur
 * {@code FOR UPDATE SKIP LOCKED}. H2 ne porte aucun des quatre.
 * <p>
 * La purge est destructrice. Chaque test verifie donc autant ce qui <b>reste</b> que ce qui
 * disparait : une purge qui supprimerait tout passerait la moitie des assertions naives.
 */
@SpringBootTest(
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4",
                // L'ordonnanceur est eteint : ces tests declenchent la purge eux-memes, et un
                // passage de fond concurrent rendrait les comptages non deterministes.
                "takibo.tas.retention.enabled=false"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class AuthorizationRetentionIntegrationTest extends TasPostgresBaseline {

    private static final Duration NO_GRACE = Duration.ZERO;
    private static final int BATCH = 10;

    @Autowired private AuthorizationRetentionPort retention;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    private TasBaselineDataset dataset;

    @BeforeEach
    void seed() {
        // Avant le jeu de donnees, et pas apres : la cle etrangere de tas_audit_events vers
        // spaces est en ON DELETE SET NULL sur le couple (org_id, space_id), alors que
        // org_id est NOT NULL. Un evenement d'audit laisse par un test precedent ferait donc
        // echouer la suppression du space au reset suivant. Defaut de schema anterieur a ce
        // recit, hors de son perimetre — contourne ici, pas masque.
        jdbc.update("DELETE FROM tas_audit_events WHERE org_id = ?", TasBaselineDataset.ORG_ID);

        dataset = new TasBaselineDataset(jdbc, new BCryptPasswordEncoder(4));
        dataset.reset();
    }

    @Test
    void given_a_mix_of_authorizations_then_only_the_fully_expired_ones_are_purged() {
        UUID expired = authorization().accessToken(hoursAgo(3)).insert();
        UUID stillActive = authorization().accessToken(hoursFromNow(1)).insert();
        UUID partiallyExpired = authorization()
                .accessToken(hoursAgo(3))
                .refreshToken(hoursFromNow(2))
                .insert();

        int deleted = retention.purgeOneBatch(NO_GRACE, BATCH);

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(expired)).isFalse();
        assertThat(exists(stillActive))
                .as("un jeton encore valide interdit la suppression")
                .isTrue();
        assertThat(exists(partiallyExpired))
                .as("un seul artefact encore actif suffit a retenir toute l'autorisation")
                .isTrue();
    }

    @Test
    void given_an_active_code_or_device_flow_then_the_authorization_survives_its_expired_tokens() {
        UUID withLiveCode = authorization()
                .accessToken(hoursAgo(5))
                .authorizationCode(hoursFromNow(1))
                .insert();
        UUID withLiveDeviceFlow = authorization()
                .accessToken(hoursAgo(5))
                .deviceCode(hoursFromNow(1))
                .userCode(hoursFromNow(1))
                .insert();

        int deleted = retention.purgeOneBatch(NO_GRACE, BATCH);

        assertThat(deleted).isZero();
        assertThat(exists(withLiveCode)).isTrue();
        assertThat(exists(withLiveDeviceFlow)).isTrue();
    }

    @Test
    void given_a_grace_period_then_a_freshly_expired_authorization_is_kept_until_it_elapses() {
        UUID justExpired = authorization().accessToken(minutesAgo(10)).insert();

        assertThat(retention.purgeOneBatch(Duration.ofHours(1), BATCH))
                .as("le delai de grace n'est pas ecoule")
                .isZero();
        assertThat(exists(justExpired)).isTrue();

        assertThat(retention.purgeOneBatch(Duration.ofMinutes(5), BATCH)).isEqualTo(1);
        assertThat(exists(justExpired)).isFalse();
    }

    @Test
    void given_more_expired_rows_than_the_batch_size_then_each_run_stays_bounded() {
        for (int i = 0; i < BATCH + 3; i++) {
            authorization().accessToken(hoursAgo(2)).insert();
        }

        assertThat(retention.purgeOneBatch(NO_GRACE, BATCH))
                .as("un lot ne depasse jamais sa borne")
                .isEqualTo(BATCH);
        assertThat(retention.purgeOneBatch(NO_GRACE, BATCH)).isEqualTo(3);
        assertThat(retention.purgeOneBatch(NO_GRACE, BATCH)).isZero();
    }

    @Test
    void given_an_already_purged_table_then_replaying_the_job_changes_nothing() {
        authorization().accessToken(hoursAgo(2)).insert();
        retention.purgeOneBatch(NO_GRACE, BATCH);

        assertThat(retention.purgeOneBatch(NO_GRACE, BATCH)).isZero();
        assertThat(retention.purgeOneBatch(NO_GRACE, BATCH)).isZero();
    }

    /**
     * Concurrence entre deux instances. Une transaction exterieure verrouille la premiere
     * ligne candidate et ne la relache pas ; la purge doit prendre les autres au lieu
     * d'attendre. C'est exactement le comportement de deux replicas qui se croisent.
     * <p>
     * Sans {@code SKIP LOCKED}, cet appel bloquerait jusqu'au {@code rollback} final et le
     * test echouerait par depassement de temps plutot que par assertion.
     */
    @Test
    void given_rows_locked_by_another_instance_then_the_purge_skips_them_instead_of_waiting()
            throws Exception {

        UUID lockedRow = authorization().accessToken(hoursAgo(9)).insert();
        UUID freeRow = authorization().accessToken(hoursAgo(2)).insert();

        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            try (PreparedStatement lock = other.prepareStatement(
                    "SELECT id FROM oauth2_authorization WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, lockedRow);
                lock.executeQuery();
            }

            int deleted = retention.purgeOneBatch(NO_GRACE, BATCH);

            assertThat(deleted)
                    .as("la ligne verrouillee est ignoree, l'autre est bien traitee")
                    .isEqualTo(1);
            assertThat(exists(freeRow)).isFalse();
            assertThat(exists(lockedRow)).isTrue();

            other.rollback();
        }

        assertThat(retention.purgeOneBatch(NO_GRACE, BATCH))
                .as("une fois relachee, la ligne redevient purgeable")
                .isEqualTo(1);
    }

    @Test
    void given_an_authorization_without_any_expiry_then_it_is_never_purged_but_it_is_counted() {
        long before = retention.countUnpurgeable();
        UUID noArtifact = authorization().insert();

        assertThat(retention.purgeOneBatch(NO_GRACE, BATCH)).isZero();
        assertThat(exists(noArtifact))
                .as("fail-closed : une ligne sans echeance exploitable n'est jamais detruite")
                .isTrue();
        assertThat(retention.countUnpurgeable()).isEqualTo(before + 1);
    }

    /**
     * La purge est physique, l'audit ne l'est pas. Detruire la ligne d'une autorisation ne
     * doit effacer aucune trace de ce qui s'est passe : la conservation de l'audit est une
     * politique distincte, decidee ailleurs et sur d'autres durees.
     * <p>
     * Le lien est volontairement teste par un evenement qui <b>designe</b> l'autorisation
     * purgee : c'est le cas ou une cascade mal posee ferait disparaitre la trace avec elle.
     */
    @Test
    void given_a_purge_then_the_audit_trail_of_the_deleted_authorization_survives() {
        UUID expired = authorization().accessToken(hoursAgo(4)).insert();
        UUID auditEvent = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tas_audit_events (
                    event_id, org_id, space_id, event_type, status, client_id, metadata_json)
                VALUES (?, ?, ?, 'TOKEN_ISSUED', 'SUCCESS', ?, CAST(? AS jsonb))
                """,
                auditEvent, TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                TasBaselineDataset.SPACE_CLIENT_ID,
                "{\"authorizationId\":\"" + expired + "\"}");

        assertThat(retention.purgeOneBatch(NO_GRACE, BATCH)).isEqualTo(1);

        assertThat(exists(expired)).isFalse();
        Integer surviving = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tas_audit_events WHERE event_id = ?", Integer.class, auditEvent);
        assertThat(surviving)
                .as("la trace d'audit survit a la destruction de l'autorisation qu'elle decrit")
                .isEqualTo(1);
    }

    /**
     * L'invariant qui rend {@code GREATEST} sur : un artefact present doit porter son
     * expiration. Sans lui, l'echeance consolidee ignorerait le null d'un jeton reellement
     * present et l'autorisation partirait alors qu'un artefact d'expiration inconnue existe.
     */
    @Test
    void given_an_artifact_without_expiry_then_the_database_refuses_the_row() {
        UUID id = UUID.randomUUID();

        Throwable rejection = org.assertj.core.api.Assertions.catchThrowable(() ->
                jdbc.update("""
                        INSERT INTO oauth2_authorization (
                            id, org_id, space_id, registered_client_id, subject_type,
                            principal_name, authorization_grant_type, access_token_hash)
                        VALUES (?, ?, ?, ?, 'CLIENT_APP', ?, 'client_credentials', ?)
                        """,
                        id, TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                        TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_ID,
                        hash()));

        // Nommer la contrainte attendue, et pas seulement exiger « une exception » : sans
        // cela, une faute de frappe dans l'INSERT ferait passer le test sans qu'aucun
        // invariant ne soit verifie.
        assertThat(rejection)
                .as("un jeton present sans expiration doit etre refuse a l'ecriture")
                .isNotNull()
                .hasMessageContaining("ck_oauth2_authz_access_expiry_present");
        assertThat(exists(id)).isFalse();
    }

    /**
     * Le critere « aucun scan complet ». Le plan est demande sur la selection interne, celle
     * que le purgeur execute pour choisir son lot : c'est elle que l'index doit soutenir, le
     * {@code DELETE} qui suit ne travaillant que sur des identifiants deja resolus.
     * <p>
     * Assez de lignes sont inserees pour que le planificateur ait un vrai choix a faire : sur
     * une table minuscule il prefererait legitimement un balayage, et le test ne prouverait
     * rien de ce qui se passera en production.
     */
    @Test
    void given_a_populated_table_then_the_planner_uses_the_purge_index() {
        jdbc.update("""
                INSERT INTO oauth2_authorization (
                    id, org_id, space_id, registered_client_id, subject_type, principal_name,
                    authorization_grant_type, access_token_hash, access_token_expires_at)
                SELECT gen_random_uuid(), ?, ?, ?, 'CLIENT_APP', ?, 'client_credentials',
                       md5(random()::text) || md5(random()::text),
                       NOW() - (g || ' minutes')::INTERVAL
                FROM generate_series(1, 3000) AS g
                """,
                TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_ID);
        jdbc.execute("ANALYZE oauth2_authorization");

        String plan = explainPurgeSelection();

        assertThat(plan)
                .as("la selection du lot doit passer par l'index de purge, plan obtenu :%n%s", plan)
                .contains("idx_oauth2_authz_purge_after");
        assertThat(plan)
                .as("aucun balayage complet de la table, plan obtenu :%n%s", plan)
                .doesNotContain("Seq Scan on oauth2_authorization");
    }

    // ─────────────────────────────────────────────────────────────
    // Outillage
    // ─────────────────────────────────────────────────────────────

    private String explainPurgeSelection() {
        StringBuilder plan = new StringBuilder();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     EXPLAIN SELECT id FROM oauth2_authorization
                     WHERE purge_after IS NOT NULL
                       AND purge_after < CURRENT_TIMESTAMP
                     ORDER BY purge_after, id
                     LIMIT 10
                     """)) {
            while (rows.next()) {
                plan.append(rows.getString(1)).append('\n');
            }
        } catch (Exception e) {
            throw new IllegalStateException("EXPLAIN en echec", e);
        }
        return plan.toString();
    }

    private boolean exists(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private static Instant hoursAgo(int hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }

    private static Instant minutesAgo(int minutes) {
        return Instant.now().minus(minutes, ChronoUnit.MINUTES);
    }

    private static Instant hoursFromNow(int hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS);
    }

    /** Empreinte au format impose par les contraintes de la table : 64 caracteres hexa. */
    private static String hash() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private Builder authorization() {
        return new Builder();
    }

    /**
     * Construit une autorisation minimale. Chaque artefact ajoute pose son empreinte
     * <b>et</b> son expiration : c'est la seule combinaison que la base accepte, et c'est
     * volontaire.
     */
    private final class Builder {

        private final UUID id = UUID.randomUUID();
        private Instant code;
        private Instant access;
        private Instant refresh;
        private Instant userCode;
        private Instant deviceCode;

        Builder authorizationCode(Instant expiresAt) {
            this.code = expiresAt;
            return this;
        }

        Builder accessToken(Instant expiresAt) {
            this.access = expiresAt;
            return this;
        }

        Builder refreshToken(Instant expiresAt) {
            this.refresh = expiresAt;
            return this;
        }

        Builder userCode(Instant expiresAt) {
            this.userCode = expiresAt;
            return this;
        }

        Builder deviceCode(Instant expiresAt) {
            this.deviceCode = expiresAt;
            return this;
        }

        UUID insert() {
            jdbc.update("""
                    INSERT INTO oauth2_authorization (
                        id, org_id, space_id, registered_client_id, subject_type, principal_name,
                        authorization_grant_type,
                        authorization_code_hash, authorization_code_expires_at,
                        access_token_hash,       access_token_expires_at,
                        refresh_token_hash,      refresh_token_expires_at,
                        user_code_hash,          user_code_expires_at,
                        device_code_hash,        device_code_expires_at)
                    VALUES (?, ?, ?, ?, 'CLIENT_APP', ?, 'client_credentials',
                            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id, TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                    TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_ID,
                    hashIfPresent(code), timestamp(code),
                    hashIfPresent(access), timestamp(access),
                    hashIfPresent(refresh), timestamp(refresh),
                    hashIfPresent(userCode), timestamp(userCode),
                    hashIfPresent(deviceCode), timestamp(deviceCode));
            return id;
        }

        private String hashIfPresent(Instant expiresAt) {
            return expiresAt == null ? null : hash();
        }

        private java.sql.Timestamp timestamp(Instant expiresAt) {
            return expiresAt == null ? null : java.sql.Timestamp.from(expiresAt);
        }
    }
}
