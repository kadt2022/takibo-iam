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
 * Prouve la portée des clés de signature décidée par TAS-GRANTS-02A.
 * <p>
 * TAS signe avec <b>une</b> clé de plateforme : {@code .issuer(...)} force le single-issuer
 * côté Spring Authorization Server, {@code /oauth2/jwks} est un endpoint unique, et les
 * tokens humains et machine partagent le même encodeur. Le schéma d'origine était pourtant
 * org-scopé de bout en bout, avec {@code org_id NOT NULL}.
 * <p>
 * Plutôt que de loger la clé de plateforme dans une organisation fictive — ce que le récit
 * interdit — la portée est devenue explicite : {@code org_id NULL} signifie « plateforme ».
 * Ce choix a un prix que ces tests paient comptant : <b>PostgreSQL considère les NULL comme
 * distincts</b>, donc l'index partiel d'origine, porté par {@code (org_id)}, n'aurait plus
 * rien empêché. Deux clés de plateforme actives auraient coexisté sans un mot, et le JWKS
 * aurait exposé deux émetteurs.
 * <p>
 * L'unicité est donc scindée en deux index partiels, et chacun est vérifié ici — la base
 * refuse, l'application n'a rien à surveiller.
 */
@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "security.password-encoder.bcrypt-strength=4"
})
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class SigningKeyScopeConstraintsIntegrationTest extends TasPostgresBaseline {

    private static final UUID SECOND_ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000b2");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM tas_signing_keys");
        jdbc.update("DELETE FROM organizations WHERE id = ?", SECOND_ORG_ID);
        new TasBaselineDataset(jdbc, passwordEncoder).reset();
    }

    // ---------- Clé de plateforme ----------

    @Test
    void given_a_platform_key_without_organization_then_it_is_accepted() {
        assertThatCode(() -> insertKey(null, "platform-kid-1", true, "ACTIVE"))
                .doesNotThrowAnyException();

        Long platformKeys = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tas_signing_keys WHERE org_id IS NULL", Long.class);
        assertThat(platformKeys).isEqualTo(1L);
    }

    @Test
    void given_an_active_platform_issuer_when_a_second_one_is_activated_then_rejected() {
        // Le coeur de la decision : sans l'index sur (org_id IS NULL), les deux lignes
        // passeraient, PostgreSQL tenant chaque NULL pour distinct.
        insertKey(null, "platform-kid-1", true, "ACTIVE");

        assertThatThrownBy(() -> insertKey(null, "platform-kid-2", true, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void given_a_retired_platform_issuer_then_a_new_active_one_is_accepted() {
        // La rotation en depend : l'ancienne clef reste publiee pour la verification,
        // seul le statut ACTIVE est contraint.
        insertKey(null, "platform-kid-old", true, "RETIRED");

        assertThatCode(() -> insertKey(null, "platform-kid-new", true, "ACTIVE"))
                .doesNotThrowAnyException();
    }

    @Test
    void given_an_active_platform_issuer_then_a_non_issuer_key_is_still_accepted() {
        insertKey(null, "platform-kid-1", true, "ACTIVE");

        assertThatCode(() -> insertKey(null, "platform-kid-2", false, "ACTIVE"))
                .doesNotThrowAnyException();
    }

    // ---------- Unicité globale du kid ----------

    @Test
    void given_a_kid_already_used_then_a_second_key_is_rejected_whatever_its_scope() {
        // Le JWKS etant unique, deux clefs de meme kid seraient indistinguables a la
        // verification. L'unicite ne peut donc pas rester bornee a l'organisation.
        insertKey(null, "shared-kid", false, "ACTIVE");

        assertThatThrownBy(() -> insertKey(TasBaselineDataset.ORG_ID, "shared-kid", false, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- Portée organisation, pour le jour où le multi-issuer arrivera ----------

    @Test
    void given_an_active_issuer_in_an_organization_when_a_second_one_is_activated_then_rejected() {
        insertKey(TasBaselineDataset.ORG_ID, "org-kid-1", true, "ACTIVE");

        assertThatThrownBy(() -> insertKey(TasBaselineDataset.ORG_ID, "org-kid-2", true, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void given_active_issuers_in_two_distinct_organizations_then_both_are_accepted() {
        insertSecondOrganization();
        insertKey(TasBaselineDataset.ORG_ID, "org-kid-1", true, "ACTIVE");

        assertThatCode(() -> insertKey(SECOND_ORG_ID, "org-kid-2", true, "ACTIVE"))
                .doesNotThrowAnyException();
    }

    @Test
    void given_an_active_platform_issuer_then_an_organization_issuer_remains_possible() {
        // Les deux portees coexistent : le single-issuer d'aujourd'hui ne ferme pas la porte.
        insertKey(null, "platform-kid-1", true, "ACTIVE");

        assertThatCode(() -> insertKey(TasBaselineDataset.ORG_ID, "org-kid-1", true, "ACTIVE"))
                .doesNotThrowAnyException();
    }

    // ---------- Fixtures ----------

    private void insertSecondOrganization() {
        jdbc.update("""
                INSERT INTO organizations (id, code, name, status)
                VALUES (?, 'baseline-org-2', 'Second Baseline Organization', 'ACTIVE')
                """, SECOND_ORG_ID);
    }

    private void insertKey(UUID orgId, String kid, boolean issuer, String status) {
        jdbc.update("""
                INSERT INTO tas_signing_keys (
                    id, org_id, kid, alg, kty, key_use, is_issuer, status, public_jwk_json)
                VALUES (?, ?, ?, 'RS256', 'RSA', 'sig', ?, ?, CAST(? AS jsonb))
                """,
                UUID.randomUUID(), orgId, kid, issuer, status, "{\"kty\":\"RSA\",\"kid\":\"" + kid + "\"}");
    }
}
