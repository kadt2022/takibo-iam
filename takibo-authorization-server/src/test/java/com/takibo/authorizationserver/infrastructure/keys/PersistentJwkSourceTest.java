package com.takibo.authorizationserver.infrastructure.keys;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.takibo.authorizationserver.domain.keys.model.KeyStatus;
import com.takibo.authorizationserver.domain.keys.model.TasSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;
import com.takibo.authorizationserver.infrastructure.springauthserver.keys.SigningKeysConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Attribution de la matiere privee pendant une rotation (TAS-GRANTS-02A).
 * <p>
 * Le piege que ces tests ferment : une cle <b>retiree conserve {@code is_issuer = true}</b>,
 * puisqu'elle a bel et bien emis. Pendant un chevauchement, l'ancienne et la nouvelle portent
 * donc toutes deux le drapeau. S'y fier pour choisir qui recoit la matiere privee en sortirait
 * <b>deux</b>, et {@code NimbusJwtEncoder} — qui refuse toute ambiguite — cesserait de signer.
 * <p>
 * C'est le statut, croise avec la fenetre temporelle, qui departage : l'identite de la cle
 * rendue par {@code findActivePlatformIssuer}. Le drapeau seul ne dit que « a vocation a
 * emettre ».
 */
class PersistentJwkSourceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final JWKSelector ALL = new JWKSelector(new JWKMatcher.Builder().build());

    private final SecretCipher cipher =
            new AesGcmSecretCipher(new SecretCipherKey("test-key", new byte[32]));

    // ---------- Le scenario de rotation ----------

    @Test
    void given_a_retired_issuer_and_a_new_active_one_then_only_the_active_one_is_private() {
        // Les DEUX portent is_issuer = true, comme dans une rotation reelle.
        Row retired = row("kid-old", true, KeyStatus.RETIRED);
        Row active = row("kid-new", true, KeyStatus.ACTIVE);
        PersistentJwkSource source = sourceOf(active, List.of(active, retired));

        List<JWK> jwks = source.get(ALL, null);

        assertThat(jwks).hasSize(2);
        assertThat(jwks.stream().filter(JWK::isPrivate).map(JWK::getKeyID).toList())
                .containsExactly("kid-new");
        assertThat(jwks.stream().filter(jwk -> !jwk.isPrivate()).map(JWK::getKeyID).toList())
                .containsExactly("kid-old");
    }

    @Test
    void given_that_same_rotation_then_nimbus_signs_with_the_new_key() {
        // La preuve de bout en bout : l'encodeur reel, avec le selecteur reel de la
        // configuration, doit signer sans ambiguite et avec la nouvelle cle.
        Row retired = row("kid-old", true, KeyStatus.RETIRED);
        Row active = row("kid-new", true, KeyStatus.ACTIVE);
        PersistentJwkSource source = sourceOf(active, List.of(active, retired));

        JwtEncoder encoder = new SigningKeysConfiguration().jwtEncoder(source);
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(() -> "RS256").build(),
                JwtClaimsSet.builder().subject("un-sujet")
                        .issuedAt(NOW).expiresAt(NOW.plusSeconds(300)).build()));

        assertThat(jwt.getHeaders()).containsEntry("kid", "kid-new");
    }

    @Test
    void given_a_retired_issuer_alone_then_nothing_can_sign_but_verification_survives() {
        // Aucune emettrice active : la verification des JWT deja emis doit continuer, seule
        // la signature devient impossible.
        Row retired = row("kid-old", true, KeyStatus.RETIRED);
        PersistentJwkSource source = sourceOf(null, List.of(retired));

        List<JWK> jwks = source.get(ALL, null);

        assertThat(jwks).hasSize(1);
        assertThat(jwks.get(0).isPrivate()).isFalse();
    }

    @Test
    void given_a_non_issuer_active_key_then_it_stays_public() {
        Row issuer = row("kid-issuer", true, KeyStatus.ACTIVE);
        Row verifier = row("kid-verify", false, KeyStatus.ACTIVE);
        PersistentJwkSource source = sourceOf(issuer, List.of(issuer, verifier));

        assertThat(source.get(ALL, null).stream().filter(JWK::isPrivate).map(JWK::getKeyID))
                .containsExactly("kid-issuer");
    }

    @Test
    void given_a_stale_issuer_snapshot_then_get_still_derives_the_signer_from_publishable_alone() {
        // Revue Codex : deux requetes separees (l'emettrice, puis les cles publiables)
        // s'exposaient a une rotation commise entre les deux. Meme si le depot renvoyait un
        // etat perime pour findActivePlatformIssuer, get() ne l'interroge plus — l'emettrice
        // est desormais designee depuis la meme liste que celle qui est publiee.
        Row staleIssuer = row("kid-old", true, KeyStatus.RETIRED);
        Row newIssuer = row("kid-new", true, KeyStatus.ACTIVE);
        PersistentJwkSource source = sourceOf(staleIssuer, List.of(newIssuer, staleIssuer));

        assertThat(source.get(ALL, null).stream().filter(JWK::isPrivate).map(JWK::getKeyID))
                .containsExactly("kid-new");
    }

    // ---------- Fail-closed au demarrage ----------

    @Test
    void given_no_active_issuer_then_the_context_refuses_to_start() {
        // Decouvrir l'absence a la premiere demande de token produirait des refus
        // incomprehensibles en service.
        PersistentJwkSource source = sourceOf(null, List.of(row("kid-old", true, KeyStatus.RETIRED)));

        assertThatThrownBy(source::afterPropertiesSet)
                .isInstanceOf(SigningKeyUnavailableException.class)
                .hasMessageContaining("NO_ACTIVE_PLATFORM_SIGNING_KEY");
    }

    @Test
    void given_an_issuer_without_private_material_then_the_context_refuses_to_start() {
        Row issuer = row("kid-issuer", true, KeyStatus.ACTIVE);
        issuer.privateKeyEncrypted = null;
        PersistentJwkSource source = sourceOf(issuer, List.of(issuer));

        assertThatThrownBy(source::afterPropertiesSet)
                .isInstanceOf(SigningKeyUnavailableException.class)
                .hasMessageContaining("ISSUER_KEY_HAS_NO_PRIVATE_MATERIAL");
    }

    @Test
    void given_undecryptable_private_material_then_the_context_refuses_to_start() {
        Row issuer = row("kid-issuer", true, KeyStatus.ACTIVE);
        issuer.privateKeyEncrypted = "v1$test-key$" + java.util.Base64.getEncoder()
                .encodeToString(new byte[64]);
        PersistentJwkSource source = sourceOf(issuer, List.of(issuer));

        assertThatThrownBy(source::afterPropertiesSet).isInstanceOf(RuntimeException.class);
    }

    @Test
    void given_a_sound_issuer_then_startup_succeeds() {
        Row issuer = row("kid-issuer", true, KeyStatus.ACTIVE);

        assertThatCode(sourceOf(issuer, List.of(issuer))::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void given_a_publishable_non_issuer_key_with_private_parameters_then_startup_refuses() {
        // Revue Codex : sans ceci, seule l'emettrice etait validee au demarrage. Une cle
        // simplement publiee (retiree ou co-emettrice) avec un JWK public incoherent ne se
        // revelait qu'a la premiere requete JWKS ou au premier decodage qui la rencontrait.
        Row issuer = row("kid-issuer", true, KeyStatus.ACTIVE);
        Row leaky = row("kid-leaky", false, KeyStatus.ACTIVE);
        leaky.publicJwkJson = jsonOf(leaky.key);
        PersistentJwkSource source = sourceOf(issuer, List.of(issuer, leaky));

        assertThatThrownBy(source::afterPropertiesSet)
                .isInstanceOf(SigningKeyUnavailableException.class)
                .hasMessageContaining("PUBLIC_JWK_CONTAINS_PRIVATE_PARAMETERS");
    }

    // ---------- La cle qui signe est celle qu'on annonce ----------

    @Test
    void given_a_private_key_that_differs_from_the_published_one_then_it_is_refused() {
        // Sans ce controle, TAS signerait avec une cle et en annoncerait une autre : chaque
        // token deviendrait invalide pour tous ses consommateurs, sans le moindre signal.
        Row issuer = row("kid-issuer", true, KeyStatus.ACTIVE);
        issuer.publicJwkJson = publicMapOf(rsa("kid-issuer"));

        PersistentJwkSource source = sourceOf(issuer, List.of(issuer));

        assertThatThrownBy(source::afterPropertiesSet)
                .isInstanceOf(SigningKeyUnavailableException.class)
                .hasMessageContaining("ISSUER_KEY_DOES_NOT_MATCH_ITS_PUBLISHED_JWK");
    }

    @Test
    void given_a_published_jwk_with_another_kid_then_it_is_refused() {
        Row issuer = row("kid-issuer", true, KeyStatus.ACTIVE);
        RSAKey renamed = issuer.key.toPublicJWK().toRSAKey();
        issuer.publicJwkJson = jsonOf(new RSAKey.Builder(renamed).keyID("kid-autre").build());

        PersistentJwkSource source = sourceOf(issuer, List.of(issuer));

        assertThatThrownBy(source::afterPropertiesSet)
                .isInstanceOf(SigningKeyUnavailableException.class)
                .hasMessageContaining("ISSUER_KEY_DOES_NOT_MATCH_ITS_PUBLISHED_JWK");
    }

    @Test
    void given_a_public_jwk_column_holding_private_parameters_then_it_is_refused() {
        // public_jwk_json est publie tel quel par l'endpoint JWKS : il ne doit jamais porter
        // de parametre prive.
        Row issuer = row("kid-issuer", true, KeyStatus.ACTIVE);
        Row leaky = row("kid-leaky", false, KeyStatus.ACTIVE);
        leaky.publicJwkJson = jsonOf(leaky.key);
        PersistentJwkSource source = sourceOf(issuer, List.of(issuer, leaky));

        assertThatThrownBy(() -> source.get(ALL, null))
                .isInstanceOf(SigningKeyUnavailableException.class)
                .hasMessageContaining("PUBLIC_JWK_CONTAINS_PRIVATE_PARAMETERS");
    }

    // ---------- Fixtures ----------

    private PersistentJwkSource sourceOf(Row issuer, List<Row> publishable) {
        return new PersistentJwkSource(new StubRepository(issuer, publishable), cipher, CLOCK);
    }

    private static final class Row {
        final UUID id = UUID.randomUUID();
        final RSAKey key;
        final String kid;
        final boolean issuerFlag;
        final KeyStatus status;
        java.util.Map<String, Object> publicJwkJson;
        String privateKeyEncrypted;

        Row(RSAKey key, String kid, boolean issuerFlag, KeyStatus status) {
            this.key = key;
            this.kid = kid;
            this.issuerFlag = issuerFlag;
            this.status = status;
            this.publicJwkJson = publicMapOf(key);
        }

        TasSigningKey toDomain() {
            return new TasSigningKey(id, null, kid, "RS256", "RSA", "sig", issuerFlag, status,
                    publicJwkJson, privateKeyEncrypted, null, null, null, null, null);
        }
    }

    private Row row(String kid, boolean issuerFlag, KeyStatus status) {
        Row row = new Row(rsa(kid), kid, issuerFlag, status);
        row.privateKeyEncrypted =
                cipher.encrypt(SecretContext.signingKeyMaterial(kid), row.key.toJSONString());
        return row;
    }

    private static java.util.Map<String, Object> jsonOf(JWK jwk) {
        try {
            return JSONObjectUtils.parse(jwk.toJSONString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static java.util.Map<String, Object> publicMapOf(RSAKey key) {
        try {
            return JSONObjectUtils.parse(key.toPublicJWK().toJSONString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey rsa(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Depot simule : ce test porte sur l'attribution de la matiere, pas sur les requetes. */
    private record StubRepository(Row issuer, List<Row> publishable) implements SigningKeyRepository {

        @Override
        public Optional<TasSigningKey> findActivePlatformIssuer(Instant at) {
            return Optional.ofNullable(issuer).map(Row::toDomain);
        }

        @Override
        public List<TasSigningKey> findPublishable(Instant at) {
            List<TasSigningKey> keys = new ArrayList<>();
            publishable.forEach(row -> keys.add(row.toDomain()));
            return keys;
        }
    }
}
