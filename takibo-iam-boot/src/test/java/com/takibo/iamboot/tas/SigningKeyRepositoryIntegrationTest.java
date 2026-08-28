package com.takibo.iamboot.tas;

import com.takibo.authorizationserver.domain.keys.model.TasSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regles de selection des cles de signature (TAS-GRANTS-02A).
 * <p>
 * Deux questions distinctes, et c'est la distinction qui porte la rotation : quelle cle signe
 * maintenant, et lesquelles doivent encore etre publiees pour que les JWT deja emis restent
 * verifiables.
 * <p>
 * Une cle <b>retiree</b> ne signe plus mais verifie encore, jusqu'a l'expiration du dernier
 * token qu'elle a signe. Une cle <b>revoquee</b> ne verifie plus rien. Confondre les deux,
 * c'est soit invalider des tokens valides, soit continuer d'accepter ceux d'une cle
 * compromise. Ces tests fixent la frontiere.
 */
@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "security.password-encoder.bcrypt-strength=4"
})
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class SigningKeyRepositoryIntegrationTest extends TasPostgresBaseline {

    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");

    @Autowired private SigningKeyRepository signingKeys;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM tas_signing_keys");
        new TasBaselineDataset(jdbc, passwordEncoder).reset();
    }

    // ---------- La cle qui signe ----------

    @Test
    void given_an_active_platform_issuer_then_it_is_the_signing_key() {
        insertKey("kid-active", true, "ACTIVE", null, null, null, null);

        assertThat(signingKeys.findActivePlatformIssuer(NOW))
                .map(TasSigningKey::kid)
                .contains("kid-active");
    }

    @Test
    void given_no_active_issuer_then_none_is_returned() {
        // Pas une situation degradee : TAS ne peut rien emettre, et le demarrage doit le dire.
        insertKey("kid-retired", true, "RETIRED", null, null, null, null);

        assertThat(signingKeys.findActivePlatformIssuer(NOW)).isEmpty();
    }

    @Test
    void given_an_active_key_that_is_not_the_issuer_then_it_does_not_sign() {
        insertKey("kid-verify-only", false, "ACTIVE", null, null, null, null);

        assertThat(signingKeys.findActivePlatformIssuer(NOW)).isEmpty();
    }

    @Test
    void given_an_organization_scoped_issuer_then_it_never_signs_for_the_platform() {
        // Des cles org-scopees peuvent exister en base ; tant que TAS est single-issuer,
        // elles ne doivent jamais etre servies.
        insertKey("kid-org", true, "ACTIVE", TasBaselineDataset.ORG_ID, null, null, null);

        assertThat(signingKeys.findActivePlatformIssuer(NOW)).isEmpty();
        assertThat(signingKeys.findPublishable(NOW)).isEmpty();
    }

    // ---------- Fenetre temporelle : validite (not_before / expires_at) ----------

    @Test
    void given_an_issuer_not_yet_valid_then_it_does_not_sign() {
        insertKey("kid-future", true, "ACTIVE", null, NOW.plus(1, ChronoUnit.HOURS), null, null);

        assertThat(signingKeys.findActivePlatformIssuer(NOW)).isEmpty();
        assertThat(signingKeys.findPublishable(NOW)).isEmpty();
    }

    @Test
    void given_a_key_past_its_cryptoperiod_then_it_does_not_sign_but_stays_published() {
        // expires_at borne la periode de validite (cryptoperiode), pas la publication : une
        // cle qui l'a depassee cesse de signer, mais publish_until, distinct et ici absent,
        // continue seul a gouverner la publication JWKS.
        insertKey("kid-expired", true, "ACTIVE", null, null,
                NOW.minus(1, ChronoUnit.SECONDS), null);

        assertThat(signingKeys.findActivePlatformIssuer(NOW)).isEmpty();
        assertThat(signingKeys.findPublishable(NOW))
                .extracting(TasSigningKey::kid)
                .containsExactly("kid-expired");
    }

    @Test
    void given_bounds_that_straddle_the_instant_then_the_key_is_valid() {
        insertKey("kid-window", true, "ACTIVE", null,
                NOW.minus(1, ChronoUnit.HOURS), NOW.plus(1, ChronoUnit.HOURS), null);

        assertThat(signingKeys.findActivePlatformIssuer(NOW))
                .map(TasSigningKey::kid)
                .contains("kid-window");
    }

    @Test
    void given_absent_bounds_then_they_do_not_restrict_anything() {
        // Une borne absente signifie « pas de borne », jamais « bornee a maintenant ».
        insertKey("kid-unbounded", true, "ACTIVE", null, null, null, null);

        assertThat(signingKeys.findActivePlatformIssuer(Instant.parse("2000-01-01T00:00:00Z")))
                .isPresent();
        assertThat(signingKeys.findActivePlatformIssuer(Instant.parse("2099-01-01T00:00:00Z")))
                .isPresent();
    }

    // ---------- Ce qui reste publie (publish_until) ----------

    @Test
    void given_a_retired_key_then_it_is_still_published_for_verification() {
        // Le coeur du chevauchement : elle ne signe plus, mais les tokens qu'elle a signes
        // doivent rester verifiables.
        insertKey("kid-retired", true, "RETIRED", null, null, null, null);

        assertThat(signingKeys.findPublishable(NOW))
                .extracting(TasSigningKey::kid)
                .containsExactly("kid-retired");
    }

    @Test
    void given_a_retired_key_past_its_publish_until_then_it_stops_being_published() {
        insertKey("kid-old", true, "RETIRED", null, null, null,
                NOW.minus(1, ChronoUnit.SECONDS));

        assertThat(signingKeys.findPublishable(NOW)).isEmpty();
    }

    @Test
    void given_a_retired_key_before_its_publish_until_then_it_is_still_published() {
        insertKey("kid-still-published", true, "RETIRED", null, null, null,
                NOW.plus(1, ChronoUnit.HOURS));

        assertThat(signingKeys.findPublishable(NOW))
                .extracting(TasSigningKey::kid)
                .containsExactly("kid-still-published");
    }

    @Test
    void given_a_revoked_key_then_it_is_never_published() {
        // Revoquee ne verifie plus rien : continuer a la publier reviendrait a accepter les
        // tokens d'une cle compromise.
        insertKey("kid-revoked", true, "REVOKED", null, null, null, null);

        assertThat(signingKeys.findPublishable(NOW)).isEmpty();
        assertThat(signingKeys.findActivePlatformIssuer(NOW)).isEmpty();
    }

    @Test
    void given_an_overlap_then_both_keys_are_published_with_the_issuer_first() {
        insertKey("kid-previous", false, "RETIRED", null, null, null, null);
        insertKey("kid-current", true, "ACTIVE", null, null, null, null);

        assertThat(signingKeys.findPublishable(NOW))
                .extracting(TasSigningKey::kid)
                .containsExactly("kid-current", "kid-previous");
    }

    @Test
    void given_a_published_key_then_its_encrypted_material_travels_untouched() {
        // Le depot ne dechiffre rien : seul le JWKSource connait le contexte a fournir.
        insertKey("kid-active", true, "ACTIVE", null, null, null, null);

        assertThat(signingKeys.findActivePlatformIssuer(NOW))
                .map(TasSigningKey::privateKeyEncrypted)
                .contains("v1$test-key$Zm9v");
    }

    @Test
    void given_no_instant_then_the_lookup_is_refused() {
        // L'assertion porte sur le message, non sur le type : l'adaptateur est annote
        // @Repository, donc la traduction d'exceptions de Spring enveloppe l'argument
        // invalide dans un InvalidDataAccessApiUsageException. Le refus est ce qui compte,
        // et il reste lisible.
        assertThatThrownBy(() -> signingKeys.findActivePlatformIssuer(null))
                .hasMessageContaining("SIGNING_KEY_LOOKUP_REQUIRES_AN_INSTANT");
    }

    private void insertKey(String kid, boolean issuer, String status, UUID orgId,
                           Instant notBefore, Instant expiresAt, Instant publishUntil) {
        jdbc.update("""
                INSERT INTO tas_signing_keys (
                    id, org_id, kid, alg, kty, key_use, is_issuer, status,
                    public_jwk_json, private_key_encrypted, not_before, expires_at, publish_until)
                VALUES (?, ?, ?, 'RS256', 'RSA', 'sig', ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
                """,
                UUID.randomUUID(), orgId, kid, issuer, status,
                "{\"kty\":\"RSA\",\"kid\":\"" + kid + "\"}",
                "v1$test-key$Zm9v",
                offset(notBefore), offset(expiresAt), offset(publishUntil));
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

}
