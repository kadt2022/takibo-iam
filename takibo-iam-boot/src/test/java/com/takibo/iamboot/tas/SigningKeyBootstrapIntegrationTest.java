package com.takibo.iamboot.tas;

import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.takibo.authorizationserver.domain.keys.PlatformSigningKeyBootstrap;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter;
import com.takibo.authorizationserver.infrastructure.keys.AesGcmSecretCipher;
import com.takibo.authorizationserver.infrastructure.keys.PersistentJwkSource;
import com.takibo.authorizationserver.infrastructure.keys.RsaSigningKeyGenerator;
import com.takibo.authorizationserver.infrastructure.keys.SecretCipherKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Amorçage automatique de la première clé de signature, sur PostgreSQL réel
 * (TAS-KEYS-BOOTSTRAP-01).
 * <p>
 * L'arbitrage de la course appartient à la base — index unique partiel
 * {@code uk_tas_sk_platform_issuer_active} et {@code ON CONFLICT ... DO NOTHING} — donc rien
 * de ce récit ne se prouve sur un double simulé. C'est aussi pourquoi cette classe construit
 * l'amorçage directement plutôt que de le résoudre depuis le contexte Spring : le contexte
 * amorce une fois pour toutes à son démarrage, alors que ces preuves demandent de partir d'une
 * table vide, d'un historique incohérent, ou de deux amorceurs simultanés.
 */
@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "security.password-encoder.bcrypt-strength=4"
})
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class SigningKeyBootstrapIntegrationTest extends TasPostgresBaseline {

    private static final SecretCipherKey CIPHER_KEY = aCipherKey("bootstrap-test-key", 7);

    @Autowired private SigningKeyRepository signingKeys;
    @Autowired private SigningKeyWriter signingKeyWriter;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private final SecretCipher cipher = new AesGcmSecretCipher(CIPHER_KEY);
    private final Clock clock = Clock.systemUTC();

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM tas_signing_keys");
        new TasBaselineDataset(jdbc, passwordEncoder).reset();
    }

    // ---------- Installation vierge ----------

    @Test
    void given_an_empty_installation_then_exactly_one_platform_issuer_is_created() {
        PlatformSigningKeyBootstrap.Outcome outcome = bootstrap().ensurePlatformIssuer();

        assertThat(outcome.created()).isTrue();
        assertThat(activeIssuerCount()).isEqualTo(1L);
        assertThat(currentIssuerKid()).isEqualTo(outcome.kid());
    }

    @Test
    void given_a_restart_then_the_same_key_is_reused_and_no_other_is_created() {
        String firstKid = bootstrap().ensurePlatformIssuer().kid();

        // Un second amorceur, construit de zéro : c'est ce que fait un redémarrage.
        PlatformSigningKeyBootstrap.Outcome afterRestart = bootstrap().ensurePlatformIssuer();

        assertThat(afterRestart.kid()).isEqualTo(firstKid);
        assertThat(afterRestart.created()).isFalse();
        assertThat(totalPlatformKeys()).isEqualTo(1L);
    }

    @Test
    void given_a_bootstrapped_key_then_it_is_readable_and_usable_after_a_restart() {
        String kid = bootstrap().ensurePlatformIssuer().kid();

        // La preuve que la matière privée a été scellée avec la clé AES externe et se
        // redéchiffre : PersistentJwkSource valide et publie, sans état partagé avec
        // l'amorçage.
        JWKSource<SecurityContext> jwkSource = new PersistentJwkSource(signingKeys, cipher, clock);
        List<String> published = publishedKids(jwkSource);

        assertThat(published).containsExactly(kid);
    }

    // ---------- Historique incohérent ----------

    @Test
    void given_a_key_history_without_an_active_issuer_then_the_startup_is_refused() {
        String kid = bootstrap().ensurePlatformIssuer().kid();
        // Ce que produirait une rotation interrompue ou une restauration partielle : la trace
        // de la clé reste, mais plus rien ne signe.
        jdbc.update("UPDATE tas_signing_keys SET status = 'RETIRED' WHERE kid = ?", kid);

        PlatformSigningKeyBootstrap afterCorruption = bootstrap();

        assertThatThrownBy(afterCorruption::ensurePlatformIssuer)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATFORM_SIGNING_KEY_HISTORY_WITHOUT_ACTIVE_ISSUER");

        // Rien n'a été fabriqué pour « réparer » la base : c'est tout l'enjeu du refus.
        assertThat(totalPlatformKeys()).isEqualTo(1L);
        assertThat(activeIssuerCount()).isZero();
    }

    // ---------- Démarrages concurrents ----------

    @Test
    void given_two_concurrent_bootstraps_then_one_key_is_created_and_both_adopt_it()
            throws Exception {
        int starters = 2;
        ExecutorService pool = Executors.newFixedThreadPool(starters);
        CountDownLatch ready = new CountDownLatch(starters);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<PlatformSigningKeyBootstrap.Outcome>> results;
        try {
            List<Callable<PlatformSigningKeyBootstrap.Outcome>> starts = List.of(
                    startingInstance(ready, go), startingInstance(ready, go));
            results = starts.stream().map(pool::submit).toList();
            ready.await();
            go.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        }

        PlatformSigningKeyBootstrap.Outcome first = results.get(0).get();
        PlatformSigningKeyBootstrap.Outcome second = results.get(1).get();

        // La preuve complète : non seulement une seule ligne subsiste, mais les deux
        // instances repartent avec le kid de la même clé gagnante — aucune ne démarre sur une
        // clé que la base n'a pas retenue, et aucune n'échoue.
        assertThat(first.kid()).isEqualTo(second.kid());
        assertThat(totalPlatformKeys()).isEqualTo(1L);
        assertThat(activeIssuerCount()).isEqualTo(1L);
        assertThat(currentIssuerKid()).isEqualTo(first.kid());

        // Exactement une des deux a inséré ; l'autre a adopté sans écrire.
        assertThat(List.of(first.created(), second.created()))
                .containsExactlyInAnyOrder(true, false);
    }

    // ---------- Fixtures ----------

    private Callable<PlatformSigningKeyBootstrap.Outcome> startingInstance(CountDownLatch ready,
                                                                          CountDownLatch go) {
        return () -> {
            // La clé RSA est tirée avant le départ commun : sans cela, la course opposerait
            // surtout les durées de génération et l'une gagnerait presque toujours.
            PlatformSigningKeyBootstrap instance = bootstrap();
            ready.countDown();
            go.await();
            return instance.ensurePlatformIssuer();
        };
    }

    private PlatformSigningKeyBootstrap bootstrap() {
        return new PlatformSigningKeyBootstrap(
                signingKeys, signingKeyWriter, new RsaSigningKeyGenerator(), cipher, clock);
    }

    private List<String> publishedKids(JWKSource<SecurityContext> jwkSource) throws RuntimeException {
        try {
            return new JWKSet(jwkSource.get(new JWKSelector(new JWKMatcher.Builder().build()), null))
                    .getKeys().stream()
                    .map(com.nimbusds.jose.jwk.JWK::getKeyID)
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Long activeIssuerCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM tas_signing_keys
                 WHERE org_id IS NULL AND is_issuer = TRUE AND status = 'ACTIVE'
                """, Long.class);
    }

    private Long totalPlatformKeys() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM tas_signing_keys WHERE org_id IS NULL", Long.class);
    }

    private String currentIssuerKid() {
        return jdbc.queryForObject("""
                SELECT kid FROM tas_signing_keys
                 WHERE org_id IS NULL AND is_issuer = TRUE AND status = 'ACTIVE'
                """, String.class);
    }

    private static SecretCipherKey aCipherKey(String id, long seed) {
        byte[] material = new byte[32];
        new SecureRandom(String.valueOf(seed).getBytes()).nextBytes(material);
        return new SecretCipherKey(id, material);
    }
}
