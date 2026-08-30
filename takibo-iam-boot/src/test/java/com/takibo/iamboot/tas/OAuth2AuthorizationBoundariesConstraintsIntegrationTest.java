package com.takibo.iamboot.tas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prouve les frontières et sujets réels de {@code oauth2_authorization} et
 * {@code oauth2_authorization_consent} décidées par TAS-GRANTS-02
 * (V202608290001__tas__oauth2_authorization_and_consent_boundaries).
 * <p>
 * Le schéma d'origine (V202601091233) rendait ces deux tables inutilisables pour ce que ce
 * récit doit y écrire : {@code org_id}/{@code space_id} NOT NULL excluaient toute autorisation
 * PLATFORM (postman-client), {@code principal_account_id} NOT NULL excluait tout
 * {@code client_credentials} (le principal y est le client lui-même, jamais un compte), et
 * l'unicité des hash bornée à {@code (org_id, space_id, hash)} — comme {@code tas_signing_keys}
 * avant TAS-GRANTS-02A — aurait laissé deux lignes PLATFORM (org_id NULL, space_id NULL)
 * partager le même hash sans qu'aucune contrainte ne le remarque, PostgreSQL traitant deux NULL
 * comme distincts.
 */
@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "security.password-encoder.bcrypt-strength=4"
})
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class OAuth2AuthorizationBoundariesConstraintsIntegrationTest extends TasPostgresBaseline {

    private static final UUID SECOND_ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000c3");
    private static final UUID SECOND_ORG_ACCOUNT_ID =
            UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000c4");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM oauth2_authorization_consent");
        jdbc.update("DELETE FROM oauth2_authorization");
        jdbc.update("DELETE FROM accounts WHERE org_id = ?", SECOND_ORG_ID);
        jdbc.update("DELETE FROM organizations WHERE id = ?", SECOND_ORG_ID);
        new TasBaselineDataset(jdbc, passwordEncoder).reset();
    }

    // ---------- oauth2_authorization : plan et frontiere ----------

    @Test
    void given_a_platform_authorization_without_org_or_space_then_it_is_accepted() {
        assertThatCode(() -> insertAuthorization(
                UUID.randomUUID(), null, null, "CLIENT_APP", null, "postman-client"))
                .doesNotThrowAnyException();
    }

    @Test
    void given_a_complete_space_authorization_then_it_is_accepted() {
        assertThatCode(() -> insertAuthorization(
                UUID.randomUUID(), TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                "CLIENT_APP", null, TasBaselineDataset.SPACE_CLIENT_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void given_space_without_org_then_it_is_rejected() {
        // La combinaison qu'aucun plan ne represente : ORGANIZATION exige org sans space,
        // SPACE exige les deux, PLATFORM aucun des deux. Un space sans org n'est aucun d'eux.
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> insertAuthorization(
                id, null, TasBaselineDataset.SPACE_ID, "CLIENT_APP", null, "orphan-space"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void given_a_client_app_subject_with_a_principal_account_then_it_is_rejected() {
        // client_credentials : le principal EST le client (OAuth2Authorization.principalName
        // porte le client_id), jamais un compte humain.
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> insertAuthorization(
                id, TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                "CLIENT_APP", TasBaselineDataset.ACCOUNT_ID, TasBaselineDataset.SPACE_CLIENT_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void given_a_human_subject_with_a_principal_account_then_it_is_accepted() {
        assertThatCode(() -> insertAuthorization(
                UUID.randomUUID(), TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                "HUMAN", TasBaselineDataset.ACCOUNT_ID, "baseline@takibo.test"))
                .doesNotThrowAnyException();
    }

    @Test
    void given_a_human_subject_without_a_principal_account_then_it_is_accepted() {
        // Device code pas encore approuve : un sujet humain existe conceptuellement, mais
        // aucun compte n'est encore etabli.
        assertThatCode(() -> insertAuthorization(
                UUID.randomUUID(), TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                "HUMAN", null, "pending-device-user"))
                .doesNotThrowAnyException();
    }

    @Test
    void given_an_unknown_subject_type_then_it_is_rejected() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> insertAuthorization(id, null, null, "ROBOT", null, "postman-client"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void given_a_registered_client_id_absent_from_oauth2_clients_then_it_is_still_accepted() {
        // postman-client (source PLATFORM in-memory, TAS-GRANTS-01) n'a aucune ligne dans
        // oauth2_clients. fk_oauth2_authz_client_scope, qui referencait le client_id public,
        // aurait rejete cette ligne ; elle est retiree au profit d'une resolvabilite verifiee
        // a la lecture par ResolvedOAuthClientResolver, pas par une FK.
        assertThatCode(() -> insertAuthorization(
                UUID.randomUUID(), null, null, "CLIENT_APP", null,
                "registered-client-id-with-no-tms-row"))
                .doesNotThrowAnyException();
    }

    // ---------- oauth2_authorization : unicite globale des hash ----------

    @Test
    void given_two_platform_authorizations_with_the_same_access_token_hash_then_rejected() {
        // Le coeur de la decision : sans l'index global, deux lignes PLATFORM (org_id NULL,
        // space_id NULL) passeraient, PostgreSQL tenant chaque NULL pour distinct — exactement
        // ce que V202608270001 a du corriger pour tas_signing_keys.
        String hash = "a".repeat(64);
        insertAuthorizationWithAccessTokenHash(UUID.randomUUID(), null, null, hash);
        UUID secondId = UUID.randomUUID();

        assertThatThrownBy(() -> insertAuthorizationWithAccessTokenHash(secondId, null, null, hash))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void given_a_platform_and_a_space_authorization_sharing_a_hash_then_rejected() {
        // Globale veut dire globale : meme a travers deux frontieres differentes.
        String hash = "b".repeat(64);
        insertAuthorizationWithAccessTokenHash(UUID.randomUUID(), null, null, hash);
        UUID secondId = UUID.randomUUID();

        assertThatThrownBy(() -> insertAuthorizationWithAccessTokenHash(
                secondId, TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID, hash))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void given_two_platform_authorizations_with_distinct_hashes_then_both_accepted() {
        insertAuthorizationWithAccessTokenHash(UUID.randomUUID(), null, null, "c".repeat(64));

        assertThatCode(() -> insertAuthorizationWithAccessTokenHash(
                UUID.randomUUID(), null, null, "d".repeat(64)))
                .doesNotThrowAnyException();
    }

    // ---------- oauth2_authorization_consent ----------

    @Test
    void given_a_consent_without_a_principal_account_then_it_is_accepted() {
        // OAuth2AuthorizationConsentService (tranche suivante) ne recoit de Spring
        // Authorization Server que registeredClientId/principalName/authorities, jamais un
        // identifiant de compte : voir V202608290003.
        assertThatCode(() -> insertConsent(
                UUID.randomUUID(), TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                null, "busa-finance", "no-account-yet@takibo.test"))
                .doesNotThrowAnyException();
    }

    @Test
    void given_an_organization_consent_without_space_then_it_is_accepted() {
        assertThatCode(() -> insertConsent(
                UUID.randomUUID(), TasBaselineDataset.ORG_ID, null,
                "org-scoped-client", "baseline@takibo.test"))
                .doesNotThrowAnyException();
    }

    @Test
    void given_a_consent_space_without_org_then_it_is_rejected() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> insertConsent(
                id, null, TasBaselineDataset.SPACE_ID, "orphan-space-client", "baseline@takibo.test"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void given_findById_shaped_lookup_then_it_resolves_without_any_tenant_parameter() {
        // OAuth2AuthorizationConsentService.findById(registeredClientId, principalName) ne
        // prend ni tenant ni compte : c'est cette paire, et elle seule, que la lecture utilise.
        insertConsent(UUID.randomUUID(), TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                "busa-finance", "baseline@takibo.test");

        Long found = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oauth2_authorization_consent
                WHERE registered_client_id = ? AND principal_name = ?
                """, Long.class, "busa-finance", "baseline@takibo.test");

        assertThat(found).isEqualTo(1L);
    }

    @Test
    void given_the_same_client_and_principal_in_two_different_organizations_then_rejected() {
        // Unicite globale, pas tenant-scopee : le meme couple (registered_client_id,
        // principal_name) ne peut plus exister deux fois, meme sous deux organisations
        // differentes. C'est exactement ce que uk_oauth2_consent_client_principal_global
        // remplace : l'ancien index incluait (org_id, space_id), ce qui l'aurait autorise.
        insertSecondOrganizationWithAccount();
        insertConsent(UUID.randomUUID(), TasBaselineDataset.ORG_ID, null,
                TasBaselineDataset.ACCOUNT_ID, "shared-client", "shared@takibo.test");
        UUID secondId = UUID.randomUUID();

        assertThatThrownBy(() -> insertConsent(
                secondId, SECOND_ORG_ID, null, SECOND_ORG_ACCOUNT_ID,
                "shared-client", "shared@takibo.test"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- Fixtures ----------

    private void insertSecondOrganizationWithAccount() {
        jdbc.update("""
                INSERT INTO organizations (id, code, name, status)
                VALUES (?, 'baseline-org-3', 'Third Baseline Organization', 'ACTIVE')
                """, SECOND_ORG_ID);
        jdbc.update("""
                INSERT INTO accounts (id, org_id, email, display_name)
                VALUES (?, ?, ?, ?)
                """, SECOND_ORG_ACCOUNT_ID, SECOND_ORG_ID, "second-org@takibo.test",
                "Second Org Account");
    }

    private void insertAuthorization(
            UUID id, UUID orgId, UUID spaceId, String subjectType, UUID principalAccountId,
            String principalName) {
        jdbc.update("""
                INSERT INTO oauth2_authorization (
                    id, org_id, space_id, registered_client_id, principal_account_id,
                    subject_type, principal_name, authorization_grant_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'client_credentials')
                """,
                id, orgId, spaceId, "registered-" + id, principalAccountId, subjectType,
                principalName);
    }

    private void insertAuthorizationWithAccessTokenHash(
            UUID id, UUID orgId, UUID spaceId, String accessTokenHash) {
        jdbc.update("""
                INSERT INTO oauth2_authorization (
                    id, org_id, space_id, registered_client_id, principal_account_id,
                    subject_type, principal_name, authorization_grant_type, access_token_hash)
                VALUES (?, ?, ?, ?, NULL, 'CLIENT_APP', ?, 'client_credentials', ?)
                """,
                id, orgId, spaceId, "registered-" + id, "principal-" + id, accessTokenHash);
    }

    private void insertConsent(
            UUID id, UUID orgId, UUID spaceId, String registeredClientId, String principalName) {
        insertConsent(id, orgId, spaceId, TasBaselineDataset.ACCOUNT_ID, registeredClientId, principalName);
    }

    private void insertConsent(
            UUID id, UUID orgId, UUID spaceId, UUID principalAccountId, String registeredClientId,
            String principalName) {
        jdbc.update("""
                INSERT INTO oauth2_authorization_consent (
                    id, org_id, space_id, registered_client_id, principal_account_id,
                    subject_type, principal_name, authorities)
                VALUES (?, ?, ?, ?, ?, 'HUMAN', ?, 'SCOPE_api.read')
                """,
                id, orgId, spaceId, registeredClientId, principalAccountId, principalName);
    }
}
