package com.takibo.iamboot.tas;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.takibo.authorizationserver.domain.keys.SigningKeyRotationService;
import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter;
import com.takibo.authorizationserver.infrastructure.keys.AesGcmSecretCipher;
import com.takibo.authorizationserver.infrastructure.keys.RsaSigningKeyGenerator;
import com.takibo.authorizationserver.infrastructure.keys.SecretCipherKey;
import com.takibo.iamboot.TakiboIamBootApplication;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le critère d'acceptation « redémarrage » du récit, à la lettre (TAS-GRANTS-02A).
 * <p>
 * {@code SigningKeyRotationIntegrationTest} construit ses chaînes de signature directement,
 * dans la même JVM, le même contexte Spring : cela prouve l'absence de cache mémoire partagé,
 * pas qu'un JWT survit à un redémarrage de TAKIBO. Cette classe fait la chose littérale :
 * <ol>
 *   <li>démarre un contexte Spring complet sur une base réelle ;</li>
 *   <li>émet un JWT ;</li>
 *   <li>ferme entièrement ce contexte ;</li>
 *   <li>en démarre un second, indépendant du premier ;</li>
 *   <li>vérifie le JWT émis par le premier avec le second.</li>
 * </ol>
 * <p>
 * Le conteneur PostgreSQL est propre à cette classe plutôt que partagé via
 * {@code TasPostgresBaseline} : deux contextes Spring successifs à l'intérieur d'un même test
 * n'entrent pas dans le modèle à contexte unique de {@code @SpringBootTest}, et le partage
 * aurait exigé d'élargir la visibilité du conteneur de ce socle pour un unique cas d'usage.
 * <p>
 * {@code WebApplicationType.NONE} : ce test n'a besoin d'aucun serveur HTTP, seulement des
 * beans {@code JwtEncoder}/{@code JwtDecoder} — les mêmes que ceux qui serviraient une requête
 * réelle, assemblés par le même {@code SigningKeysConfiguration}.
 */
@EnabledIf("com.takibo.iamboot.tas.TasPostgresBaseline#dockerIsAvailable")
class SigningKeyRestartAcceptanceTest {

    private static final String CIPHER_KEY_ID = "restart-test-key";
    private static final byte[] CIPHER_KEY_MATERIAL = new byte[32];
    // Distincte de CIPHER_KEY_MATERIAL : voir UserCodeHmac (TAS-GRANTS-02) sur pourquoi les
    // deux cles ne doivent jamais partager la meme matiere. Ce test ne signe ni ne verifie
    // aucun user_code, mais ephemeral=false ci-dessous active le meme bean de production
    // (SigningKeysConfiguration.userCodeHmac) que le reste de l'application : sans cette
    // propriete, le contexte complet de TakiboIamBootApplication ne demarrerait pas.
    private static final byte[] USER_CODE_HMAC_KEY_MATERIAL;

    static {
        USER_CODE_HMAC_KEY_MATERIAL = new byte[32];
        for (int i = 0; i < USER_CODE_HMAC_KEY_MATERIAL.length; i++) {
            USER_CODE_HMAC_KEY_MATERIAL[i] = (byte) (255 - i);
        }
    }

    private static PostgreSQLContainer<?> postgres;

    private static final String[] OVERRIDDEN_PROPERTIES = {
            "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "spring.datasource.driver-class-name", "spring.flyway.enabled",
            "spring.flyway.locations", "spring.jpa.hibernate.ddl-auto",
            "takibo.tas.keys.ephemeral", "takibo.tas.keys.cipher.active-key-id",
            "takibo.tas.keys.cipher.active-key", "takibo.tas.keys.user-code-hmac.key",
            "management.health.mail.enabled",
            "security.password-encoder.bcrypt-strength", "server.port"
    };

    @BeforeAll
    static void startDatabaseAndSeedTheFirstIssuer() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("takibo_iam_restart")
                .withUsername("takibo")
                .withPassword("takibo");
        postgres.start();

        overrideApplicationProperties();
        migrateSchema();
        seedFirstIssuer();
    }

    @AfterAll
    static void stopDatabaseAndClearOverrides() {
        // Des proprietes systeme non nettoyees fuiraient vers toute autre classe de test
        // executee dans la meme JVM Gradle : ephemeral=false et l'URL d'un conteneur deja
        // arrete casseraient silencieusement un @SpringBootTest execute ensuite.
        for (String property : OVERRIDDEN_PROPERTIES) {
            System.clearProperty(property);
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void given_a_jwt_signed_before_the_context_closes_then_a_fresh_context_still_verifies_it() {
        String token;
        ConfigurableApplicationContext first = bootApplication();
        try {
            JwtEncoder encoder = first.getBean(JwtEncoder.class);
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer("https://restart-acceptance-test")
                    .subject("restart-subject")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
            token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        } finally {
            // Fermeture complete, pas une simple sortie de portee : c'est elle qui prouve
            // qu'aucun etat du premier contexte ne pourrait fuiter vers le second.
            first.close();
        }

        ConfigurableApplicationContext second = bootApplication();
        try {
            JwtDecoder decoder = second.getBean(JwtDecoder.class);

            Jwt decoded = decoder.decode(token);

            assertThat(decoded.getSubject()).isEqualTo("restart-subject");
        } finally {
            second.close();
        }
    }

    /**
     * Serveur web reel, pas {@code WebApplicationType.NONE} : la chaine de securite de TAS —
     * {@code TenantSecurityConfig} entre autres — cable des filtres Spring MVC qui exigent un
     * contexte web pour se resoudre, meme si ce test n'emet aucune requete HTTP. Port 0 :
     * deux contextes se suivent ici, jamais en parallele, mais un port fixe resterait fragile
     * si un autre test tournait au meme instant dans la meme JVM Gradle.
     */
    private static ConfigurableApplicationContext bootApplication() {
        return new SpringApplicationBuilder(TakiboIamBootApplication.class)
                .profiles("test")
                .run();
    }

    /**
     * Surcharge application-test.yml (cles ephemeres, base H2) avec une priorite superieure a
     * celle d'un profil : les proprietes systeme passent avant les fichiers
     * application-{profil}.yml dans l'ordre de resolution de Spring Boot.
     */
    private static void overrideApplicationProperties() {
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgres.getUsername());
        System.setProperty("spring.datasource.password", postgres.getPassword());
        System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        System.setProperty("spring.flyway.enabled", "true");
        System.setProperty("spring.flyway.locations", "classpath:db/migration");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        System.setProperty("takibo.tas.keys.ephemeral", "false");
        System.setProperty("takibo.tas.keys.cipher.active-key-id", CIPHER_KEY_ID);
        System.setProperty("takibo.tas.keys.cipher.active-key",
                Base64.getEncoder().encodeToString(CIPHER_KEY_MATERIAL));
        System.setProperty("takibo.tas.keys.user-code-hmac.key",
                Base64.getEncoder().encodeToString(USER_CODE_HMAC_KEY_MATERIAL));
        System.setProperty("management.health.mail.enabled", "false");
        System.setProperty("security.password-encoder.bcrypt-strength", "4");
        // application.yml fixe server.port a 8081, une priorite superieure a celle des
        // proprietes par defaut passees au builder : seule une propriete systeme le surpasse.
        System.setProperty("server.port", "0");
    }

    /**
     * Migre le schema avant tout demarrage de contexte : {@code PersistentJwkSource} exige au
     * demarrage une emettrice deja active (fail-closed), donc la premiere activation ne peut
     * pas passer par un contexte Spring sans creer la dependance circulaire que ce fail-closed
     * est cense empecher — {@code SigningKeyRotationIntegrationTest} fait le meme choix.
     */
    private static void migrateSchema() {
        org.flywaydb.core.Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    /**
     * Amorce l'emettrice avec les memes classes de production que
     * {@code SigningKeysConfiguration} assemble, hors contexte Spring — seul le port
     * d'ecriture est une implementation JDBC minimale, ce test ne portant pas sur la
     * traduction entite-domaine deja couverte par {@code JpaSigningKeyRepositoryTest}.
     */
    private static void seedFirstIssuer() {
        try (HikariDataSource dataSource = dataSource()) {
            SecretCipherKey cipherKey = new SecretCipherKey(CIPHER_KEY_ID, CIPHER_KEY_MATERIAL);
            SigningKeyRotationService bootstrap = new SigningKeyRotationService(
                    new RsaSigningKeyGenerator(),
                    new JdbcFirstIssuerWriter(new JdbcTemplate(dataSource)),
                    new AesGcmSecretCipher(cipherKey),
                    Clock.systemUTC());

            bootstrap.initializeFirstIssuer();
        }
    }

    private static HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }

    /** Ecriture minimale pour l'amorcage seul ; la rotation n'a pas sa place dans ce test. */
    private record JdbcFirstIssuerWriter(JdbcTemplate jdbc) implements SigningKeyWriter {

        @Override
        public void activateFirstIssuer(NewSigningKey newKey) {
            jdbc.update("""
                    INSERT INTO tas_signing_keys (
                        id, org_id, kid, alg, kty, key_use, is_issuer, status,
                        public_jwk_json, private_key_encrypted)
                    VALUES (?, NULL, ?, ?, ?, ?, TRUE, 'ACTIVE', CAST(? AS jsonb), ?)
                    """,
                    UUID.randomUUID(), newKey.kid(), newKey.alg(), newKey.kty(), newKey.keyUse(),
                    toJson(newKey.publicJwkJson()), newKey.privateKeyEncrypted());
        }

        @Override
        public void activateNewIssuer(NewSigningKey newKey, Instant retiredKeyExpiresAt) {
            throw new UnsupportedOperationException(
                    "JdbcFirstIssuerWriter n'amorce qu'une premiere emettrice");
        }

        private static String toJson(Map<String, Object> publicJwkJson) {
            return JSONObjectUtils.toJSONString(publicJwkJson);
        }
    }
}
