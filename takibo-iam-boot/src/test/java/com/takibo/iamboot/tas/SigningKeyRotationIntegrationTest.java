package com.takibo.iamboot.tas;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.takibo.authorizationserver.domain.keys.SigningKeyRotationService;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter;
import com.takibo.authorizationserver.infrastructure.keys.AesGcmSecretCipher;
import com.takibo.authorizationserver.infrastructure.keys.PersistentJwkSource;
import com.takibo.authorizationserver.infrastructure.keys.SecretCipherKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rotation des clés de signature, de bout en bout sur PostgreSQL réel (TAS-GRANTS-02A).
 * <p>
 * Trois preuves, chacune correspondant à un critère d'acceptation du récit :
 * <ul>
 *   <li><b>redémarrage</b> — un JWT signé par une instance survit à la reconstruction complète
 *       de la chaîne de signature, sans qu'aucun état en mémoire n'ait été transmis ;</li>
 *   <li><b>chevauchement</b> — après rotation, une chaîne fraîchement construite signe avec la
 *       clé neuve, et l'ancienne reste vérifiable ;</li>
 *   <li><b>concurrence</b> — plusieurs rotations lancées en même temps laissent exactement une
 *       émettrice active, jamais deux, jamais zéro.</li>
 * </ul>
 * <p>
 * Le service de rotation et les chaînes de signature sont construits directement dans le test,
 * plutôt que résolus depuis le contexte Spring : {@code PersistentJwkSource} exige au
 * démarrage une émettrice déjà active (fail-closed), et cette classe teste justement
 * l'activation de la toute première. Passer par le contexte Spring créerait la dépendance
 * circulaire que le fail-closed est censé empêcher. La construction directe est fidèle à la
 * production — ce sont les mêmes classes, assemblées de la même façon que
 * {@code SigningKeysConfiguration} le fait.
 */
@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "security.password-encoder.bcrypt-strength=4"
})
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class SigningKeyRotationIntegrationTest extends TasPostgresBaseline {

    private static final SecretCipherKey CIPHER_KEY = aCipherKey("rotation-test-key", 42);

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

    // ---------- Redemarrage ----------

    @Test
    void given_a_token_signed_before_a_fresh_signing_chain_then_it_still_verifies_after() {
        SigningKeyRotationService rotation = rotationService();
        rotation.rotate(Duration.ofMinutes(10));

        SigningChain beforeRestart = signingChain();
        String token = beforeRestart.sign();

        // Aucune donnee ne traverse cette ligne : chaque champ est reconstruit a partir des
        // memes collaborateurs sans etat partage, ce qu'un redemarrage de processus ferait
        // exactement de la meme facon.
        SigningChain afterRestart = signingChain();

        Jwt decoded = afterRestart.decode(token);
        assertThat(decoded.getSubject()).isEqualTo("verification-subject");
    }

    @Test
    void given_two_independent_chains_then_both_trust_the_same_active_key() {
        rotationService().rotate(Duration.ofMinutes(10));

        SigningChain first = signingChain();
        SigningChain second = signingChain();

        String tokenFromFirst = first.sign();
        String tokenFromSecond = second.sign();

        // Chacune verifie le jeton de l'autre : aucune n'a besoin d'avoir signe pour verifier.
        assertThat(first.decode(tokenFromSecond).getSubject()).isEqualTo("verification-subject");
        assertThat(second.decode(tokenFromFirst).getSubject()).isEqualTo("verification-subject");
    }

    // ---------- Chevauchement ----------

    @Test
    void given_a_rotation_then_a_fresh_chain_signs_with_the_new_key_while_the_old_still_verifies() {
        SigningKeyRotationService rotation = rotationService();
        String firstKid = rotation.rotate(Duration.ofMinutes(10));

        SigningChain beforeRotation = signingChain();
        String tokenFromFirstKey = beforeRotation.sign();

        String secondKid = rotation.rotate(Duration.ofMinutes(10));
        assertThat(secondKid).isNotEqualTo(firstKid);

        SigningChain afterRotation = signingChain();
        String tokenFromSecondKey = afterRotation.sign();

        assertThat(afterRotation.decode(tokenFromSecondKey).getHeaders().get("kid"))
                .isEqualTo(secondKid);
        // Le chevauchement : le jeton scelle par l'ancienne cle reste verifiable par une
        // chaine qui ne l'a jamais signe elle-meme.
        assertThat(afterRotation.decode(tokenFromFirstKey).getHeaders().get("kid"))
                .isEqualTo(firstKid);
    }

    @Test
    void given_a_rotation_then_the_jwks_exposes_both_public_keys() throws Exception {
        SigningKeyRotationService rotation = rotationService();
        String firstKid = rotation.rotate(Duration.ofMinutes(10));
        String secondKid = rotation.rotate(Duration.ofMinutes(10));

        JWKSource<SecurityContext> jwkSource =
                new PersistentJwkSource(signingKeys, cipher, clock);
        List<String> publishedKids = new com.nimbusds.jose.jwk.JWKSet(
                jwkSource.get(new com.nimbusds.jose.jwk.JWKSelector(
                        new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null))
                .getKeys().stream()
                .map(com.nimbusds.jose.jwk.JWK::getKeyID)
                .toList();

        assertThat(publishedKids).containsExactlyInAnyOrder(firstKid, secondKid);
    }

    @Test
    void given_an_old_key_past_its_retirement_deadline_then_it_stops_being_published() {
        // Le "retrait apres expiration" du recit : aucune action supplementaire n'est
        // necessaire, findPublishable filtre deja sur expires_at.
        //
        // La duree de grace passee a rotate() borne la cle que CET APPEL retire, pas celle
        // qu'il active : la premiere rotation (amorcage) n'a rien a retirer, c'est la
        // seconde qui retire la premiere cle, avec une echeance immediate.
        SigningKeyRotationService rotation = rotationService();
        String firstKid = rotation.rotate(Duration.ofMinutes(10));
        rotation.rotate(Duration.ZERO);

        List<String> publishable = signingKeys.findPublishable(clock.instant()).stream()
                .map(k -> k.kid())
                .toList();

        assertThat(publishable).doesNotContain(firstKid);
    }

    // ---------- Concurrence ----------

    @Test
    void given_concurrent_rotations_then_exactly_one_active_platform_issuer_remains()
            throws InterruptedException {
        // Une premiere rotation sequentielle amorce l'installation, pour que les appels
        // concurrents disputent la rotation d'une emettrice EXISTANTE plutot que l'amorcage
        // d'une table vide — c'est le scenario que le critere du recit vise.
        rotationService().rotate(Duration.ofMinutes(10));

        int rotationCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(rotationCount);
        CountDownLatch ready = new CountDownLatch(rotationCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        try {
            for (int i = 0; i < rotationCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failures.incrementAndGet();
                        return;
                    }
                    try {
                        rotationService().rotate(Duration.ofMinutes(10));
                    } catch (RuntimeException e) {
                        failures.incrementAndGet();
                    }
                });
            }
            ready.await();
            go.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        // L'invariant garanti, pas l'absence d'echec : un UPDATE bloque par le verrou d'un
        // concurrent ne revalide, une fois debloque, que la ligne qu'il ciblait au depart — il
        // ne decouvre pas la nouvelle emettrice qu'un concurrent vient d'activer entre-temps.
        // Un appel peut donc chercher a inserer alors qu'une autre emettrice est deja active ;
        // c'est l'index unique partiel qui le refuse alors. Au moins une rotation reussit
        // necessairement (la premiere a s'inserer), et aucune ne doit jamais laisser deux
        // emettrices actives.
        assertThat(failures.get()).isLessThan(rotationCount);

        Long activeIssuers = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tas_signing_keys
                 WHERE org_id IS NULL AND is_issuer = TRUE AND status = 'ACTIVE'
                """, Long.class);
        assertThat(activeIssuers).isEqualTo(1L);

        // La cle d'amorcage, plus une par rotation concurrente reussie.
        long expectedTotal = 1 + (rotationCount - failures.get());
        Long totalKeys = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tas_signing_keys WHERE org_id IS NULL", Long.class);
        assertThat(totalKeys).isEqualTo(expectedTotal);

        Long distinctKids = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT kid) FROM tas_signing_keys WHERE org_id IS NULL", Long.class);
        assertThat(distinctKids).isEqualTo(expectedTotal);
    }

    @Test
    void given_successive_rotations_then_each_retired_key_remains_distinctly_identifiable() {
        // Rotations sequentielles, sans contention : verifie que la chaine retiree par chaque
        // rotation est bien identifiable individuellement, pas seulement en nombre.
        SigningKeyRotationService rotation = rotationService();
        Set<String> kids = IntStream.range(0, 4)
                .mapToObj(i -> rotation.rotate(Duration.ofMinutes(10)))
                .collect(Collectors.toSet());

        assertThat(kids).hasSize(4);

        List<String> retiredKids = jdbc.queryForList(
                "SELECT kid FROM tas_signing_keys WHERE org_id IS NULL AND status = 'RETIRED'",
                String.class);
        assertThat(retiredKids).hasSize(3).containsAll(
                kids.stream().filter(kid -> !kid.equals(currentIssuerKid())).toList());
    }

    // ---------- Fixtures ----------

    private String currentIssuerKid() {
        return signingKeys.findActivePlatformIssuer(clock.instant())
                .orElseThrow()
                .kid();
    }

    private SigningKeyRotationService rotationService() {
        return new SigningKeyRotationService(signingKeyWriter, cipher, clock);
    }

    private SigningChain signingChain() {
        JWKSource<SecurityContext> jwkSource = new PersistentJwkSource(signingKeys, cipher, clock);
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        encoder.setJwkSelector(candidates -> {
            List<com.nimbusds.jose.jwk.JWK> signing =
                    candidates.stream().filter(com.nimbusds.jose.jwk.JWK::isPrivate).toList();
            return signing.get(0);
        });
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
        return new SigningChain(encoder, decoder);
    }

    private record SigningChain(NimbusJwtEncoder encoder, NimbusJwtDecoder decoder) {
        String sign() {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer("https://rotation-test")
                    .subject("verification-subject")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300))
                    .build();
            return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        }

        Jwt decode(String token) {
            return decoder.decode(token);
        }
    }

    private static SecretCipherKey aCipherKey(String id, long seed) {
        byte[] material = new byte[32];
        new SecureRandom(String.valueOf(seed).getBytes()).nextBytes(material);
        return new SecretCipherKey(id, material);
    }
}
