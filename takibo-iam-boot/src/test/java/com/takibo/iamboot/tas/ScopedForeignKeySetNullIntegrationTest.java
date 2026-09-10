package com.takibo.iamboot.tas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prouve que les clés étrangères composites du schéma ne nullent que leur colonne
 * facultative, jamais {@code org_id}
 * (V202609090002__core__fk_set_null_scoped_columns).
 * <p>
 * Le schéma initial déclarait cinq fois le même patron : la paire
 * {@code (org_id, <colonne facultative>)} référence la clé d'unicité scopée de la table
 * cible, avec {@code ON DELETE SET NULL} sans liste de colonnes. PostgreSQL nulle alors
 * <em>toutes</em> les colonnes de la clé étrangère, {@code org_id} compris — et
 * {@code org_id} est NOT NULL dans les cinq tables. La suppression ne nullait donc jamais :
 * elle échouait sur « null value in column "org_id" ... violates not-null constraint ».
 * <p>
 * Le défaut s'est manifesté en écrivant TAS-GRANTS-02B : un événement d'audit TAS inséré par
 * un test rendait impossible le {@code DELETE FROM spaces} de
 * {@link TasBaselineDataset#clear()}, et faisait échouer le test suivant. Rien n'est propre
 * au TAS ici, mais ce paquet porte le seul socle PostgreSQL réel du dépôt
 * ({@link TasPostgresBaseline}), et c'est là que la panne a été trouvée.
 * <p>
 * H2 ne prouverait rien : {@code ON DELETE SET NULL (colonnes...)} est une syntaxe
 * PostgreSQL 15+, et c'est le comportement du moteur réel, pas la déclaration, qui est en
 * cause. Ces tests tournent sur PostgreSQL 16, la cible de la CI.
 */
@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "security.password-encoder.bcrypt-strength=4"
})
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class ScopedForeignKeySetNullIntegrationTest extends TasPostgresBaseline {

    /** Space jetable : les tests le suppriment, le client OAuth2 du socle n'y est pas rattaché. */
    private static final UUID DISPOSABLE_SPACE_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000d1");
    private static final UUID SECOND_ORG_ID =
            UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000d2");
    private static final UUID SECOND_ORG_SPACE_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000d3");
    private static final UUID CREDENTIAL_ACCOUNT_ID =
            UUID.fromString("cccccccc-0000-0000-0000-0000000000d4");
    private static final UUID IDENTITY_ID =
            UUID.fromString("eeeeeeee-0000-0000-0000-0000000000d5");
    private static final UUID UNKNOWN_SPACE_ID =
            UUID.fromString("ffffffff-0000-0000-0000-0000000000d6");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        purge();
        new TasBaselineDataset(jdbc, passwordEncoder).reset();
        insertSpace(DISPOSABLE_SPACE_ID, TasBaselineDataset.ORG_ID, "disposable-space");
    }

    /**
     * Le conteneur PostgreSQL est unique pour la JVM et partage par toutes les classes qui
     * heritent de {@link TasPostgresBaseline}. Les observations et les cles d'identite ecrites
     * ici retiennent l'organisation du socle par leurs propres FK vers {@code organizations},
     * et feraient echouer le {@code reset()} de la classe suivante. On nettoie donc apres soi,
     * pas seulement avant.
     */
    @AfterEach
    void cleanUp() {
        purge();
    }

    private void purge() {
        jdbc.update("DELETE FROM tas_audit_events");
        jdbc.update("DELETE FROM identity_observations");
        jdbc.update("DELETE FROM identity_keys");
        jdbc.update("DELETE FROM takibo_identities WHERE org_id IN (?, ?)",
                TasBaselineDataset.ORG_ID, SECOND_ORG_ID);
        jdbc.update("DELETE FROM spaces WHERE org_id = ?", SECOND_ORG_ID);
        jdbc.update("DELETE FROM accounts WHERE org_id = ?", SECOND_ORG_ID);
        jdbc.update("DELETE FROM organizations WHERE id = ?", SECOND_ORG_ID);
    }

    // ---------- tas_audit_events : la panne d'origine ----------

    @Test
    void given_an_audit_event_on_a_space_when_the_space_is_deleted_then_the_event_survives_without_its_space() {
        // Le fait central : avant la correction, ce DELETE echouait sur org_id NOT NULL.
        UUID eventId = insertAuditEvent(TasBaselineDataset.ORG_ID, DISPOSABLE_SPACE_ID);

        assertThatCode(() -> deleteSpace(DISPOSABLE_SPACE_ID)).doesNotThrowAnyException();

        Map<String, Object> event = jdbc.queryForMap(
                "SELECT org_id, space_id FROM tas_audit_events WHERE event_id = ?", eventId);
        // L'organisation est la frontiere de lecture de l'audit (RBAC-08) : la perdre
        // rendrait l'evenement illisible pour tout auditeur.
        assertThat(event.get("org_id")).isEqualTo(TasBaselineDataset.ORG_ID);
        assertThat(event.get("space_id")).isNull();
    }

    @Test
    void given_an_audit_event_when_the_baseline_dataset_is_cleared_then_the_cleanup_succeeds() {
        // La forme exacte sous laquelle la panne a ete decouverte : le nettoyage du jeu de
        // donnees d'un test faisait echouer le test suivant. Deux causes s'y superposaient,
        // et il faut les deux : le DELETE FROM spaces echouait sur org_id NOT NULL (corrige
        // par V202609090002), et le DELETE FROM organizations restait retenu par fk_tae_org,
        // que seule la purge des evenements dans clear() libere.
        insertAuditEvent(TasBaselineDataset.ORG_ID, DISPOSABLE_SPACE_ID);

        assertThatCode(() -> new TasBaselineDataset(jdbc, passwordEncoder).clear())
                .doesNotThrowAnyException();
    }

    @Test
    void given_an_audit_event_referencing_an_unknown_space_then_it_is_rejected() {
        // La cle etrangere reste verifiee a l'ecriture : la correction ne l'affaiblit pas.
        assertThatThrownBy(() -> insertAuditEvent(TasBaselineDataset.ORG_ID, UNKNOWN_SPACE_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void given_an_audit_event_whose_space_belongs_to_another_organization_then_it_is_rejected() {
        // La raison d'etre de la paire (org_id, space_id) : un evenement ne peut pas designer
        // le space d'une autre organisation. Nuller une seule colonne ne change rien a cela.
        insertSecondOrganizationWithSpace();

        assertThatThrownBy(() -> insertAuditEvent(TasBaselineDataset.ORG_ID, SECOND_ORG_SPACE_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- identity_observations : le meme patron, deux fois ----------

    @Test
    void given_an_observation_on_a_space_when_the_space_is_deleted_then_the_observation_survives_without_its_space() {
        UUID observationId = insertObservation(
                TasBaselineDataset.ORG_ID, DISPOSABLE_SPACE_ID, null);

        assertThatCode(() -> deleteSpace(DISPOSABLE_SPACE_ID)).doesNotThrowAnyException();

        Map<String, Object> observation = jdbc.queryForMap(
                "SELECT org_id, space_id FROM identity_observations WHERE observation_id = ?",
                observationId);
        assertThat(observation.get("org_id")).isEqualTo(TasBaselineDataset.ORG_ID);
        assertThat(observation.get("space_id")).isNull();
    }

    @Test
    void given_an_observation_on_an_identity_when_the_identity_is_deleted_then_the_observation_survives_without_it() {
        insertIdentity(IDENTITY_ID, TasBaselineDataset.ORG_ID, TasBaselineDataset.ACCOUNT_ID);
        UUID observationId = insertObservation(
                TasBaselineDataset.ORG_ID, null, TasBaselineDataset.ACCOUNT_ID);

        assertThatCode(() -> jdbc.update(
                "DELETE FROM takibo_identities WHERE identity_id = ?", IDENTITY_ID))
                .doesNotThrowAnyException();

        Map<String, Object> observation = jdbc.queryForMap(
                "SELECT org_id, account_id FROM identity_observations WHERE observation_id = ?",
                observationId);
        assertThat(observation.get("org_id")).isEqualTo(TasBaselineDataset.ORG_ID);
        assertThat(observation.get("account_id")).isNull();
    }

    // ---------- spaces et identity_keys : le meme patron vers accounts ----------

    @Test
    void given_a_space_owned_by_an_account_when_the_account_is_deleted_then_the_space_survives_without_owner() {
        insertAccount(CREDENTIAL_ACCOUNT_ID, TasBaselineDataset.ORG_ID, "owner@takibo.test");
        jdbc.update("UPDATE spaces SET owner_account_id = ? WHERE id = ?",
                CREDENTIAL_ACCOUNT_ID, DISPOSABLE_SPACE_ID);

        assertThatCode(() -> jdbc.update(
                "DELETE FROM accounts WHERE id = ?", CREDENTIAL_ACCOUNT_ID))
                .doesNotThrowAnyException();

        Map<String, Object> space = jdbc.queryForMap(
                "SELECT org_id, owner_account_id FROM spaces WHERE id = ?", DISPOSABLE_SPACE_ID);
        assertThat(space.get("org_id")).isEqualTo(TasBaselineDataset.ORG_ID);
        assertThat(space.get("owner_account_id")).isNull();
    }

    @Test
    void given_an_identity_key_when_its_credential_account_is_deleted_then_the_key_survives_without_it() {
        insertIdentity(IDENTITY_ID, TasBaselineDataset.ORG_ID, TasBaselineDataset.ACCOUNT_ID);
        insertAccount(CREDENTIAL_ACCOUNT_ID, TasBaselineDataset.ORG_ID, "credential@takibo.test");
        UUID keyId = insertIdentityKey(
                TasBaselineDataset.ORG_ID, TasBaselineDataset.ACCOUNT_ID, CREDENTIAL_ACCOUNT_ID);

        assertThatCode(() -> jdbc.update(
                "DELETE FROM accounts WHERE id = ?", CREDENTIAL_ACCOUNT_ID))
                .doesNotThrowAnyException();

        Map<String, Object> key = jdbc.queryForMap("""
                SELECT org_id, account_id, credential_account_id
                FROM identity_keys WHERE key_id = ?
                """, keyId);
        assertThat(key.get("org_id")).isEqualTo(TasBaselineDataset.ORG_ID);
        assertThat(key.get("account_id")).isEqualTo(TasBaselineDataset.ACCOUNT_ID);
        assertThat(key.get("credential_account_id")).isNull();
    }

    // ---------- Fixtures ----------

    private void deleteSpace(UUID spaceId) {
        jdbc.update("DELETE FROM spaces WHERE id = ?", spaceId);
    }

    private void insertSecondOrganizationWithSpace() {
        jdbc.update("""
                INSERT INTO organizations (id, code, name, status)
                VALUES (?, 'scoped-fk-org', 'Scoped FK Organization', 'ACTIVE')
                """, SECOND_ORG_ID);
        insertSpace(SECOND_ORG_SPACE_ID, SECOND_ORG_ID, "scoped-fk-space");
    }

    private void insertSpace(UUID spaceId, UUID orgId, String code) {
        jdbc.update("""
                INSERT INTO spaces (id, org_id, code, name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, spaceId, orgId, code, code);
    }

    private void insertAccount(UUID accountId, UUID orgId, String email) {
        jdbc.update("""
                INSERT INTO accounts (id, org_id, email, display_name)
                VALUES (?, ?, ?, ?)
                """, accountId, orgId, email, email);
    }

    private void insertIdentity(UUID identityId, UUID orgId, UUID accountId) {
        jdbc.update("""
                INSERT INTO takibo_identities (
                    identity_id, org_id, account_id, identity_type, identity_status)
                VALUES (?, ?, ?, 'HUMAN', 'ACTIVE')
                """, identityId, orgId, accountId);
    }

    private UUID insertIdentityKey(UUID orgId, UUID accountId, UUID credentialAccountId) {
        UUID keyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_keys (
                    key_id, org_id, account_id, key_type, key_status, credential_account_id)
                VALUES (?, ?, ?, 'PASSWORD', 'ACTIVE', ?)
                """, keyId, orgId, accountId, credentialAccountId);
        return keyId;
    }

    private UUID insertObservation(UUID orgId, UUID spaceId, UUID accountId) {
        UUID observationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_observations (
                    observation_id, org_id, space_id, account_id, observation_type, severity)
                VALUES (?, ?, ?, ?, 'LOGIN', 'INFO')
                """, observationId, orgId, spaceId, accountId);
        return observationId;
    }

    private UUID insertAuditEvent(UUID orgId, UUID spaceId) {
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tas_audit_events (event_id, org_id, space_id, event_type, status)
                VALUES (?, ?, ?, 'TOKEN_ISSUED', 'SUCCESS')
                """, eventId, orgId, spaceId);
        return eventId;
    }
}
