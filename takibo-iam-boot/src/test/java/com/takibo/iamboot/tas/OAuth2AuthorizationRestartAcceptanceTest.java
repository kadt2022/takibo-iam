package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le critère d'acceptation « redémarrage » de TAS-GRANTS-02, à la lettre — même démarche que
 * {@link SigningKeyRestartAcceptanceTest} pour TAS-GRANTS-02A, appliquée à l'autorisation :
 * <ol>
 *   <li>démarre un contexte Spring complet sur une base réelle ;</li>
 *   <li>émet un token {@code client_credentials} via une vraie requête HTTP ;</li>
 *   <li>ferme entièrement ce contexte ;</li>
 *   <li>en démarre un second, indépendant du premier ;</li>
 *   <li>retrouve l'autorisation persistée par le premier via
 *       {@code OAuth2AuthorizationService.findByToken} du second.</li>
 * </ol>
 * <p>
 * Une seule et même clé de chiffrement persistante (jamais éphémère) traverse les deux
 * contextes : c'est elle qui rend le chiffre du premier lisible par le second, exactement
 * comme le ferait un redémarrage réel de TAKIBO en production.
 */
@EnabledIf("com.takibo.iamboot.tas.TasPostgresBaseline#dockerIsAvailable")
class OAuth2AuthorizationRestartAcceptanceTest {

    private static final String CIPHER_KEY_ID = "oauth2-authz-restart-test-key";
    private static final byte[] CIPHER_KEY_MATERIAL = new byte[32];
    // Distincte de CIPHER_KEY_MATERIAL : voir UserCodeHmac sur pourquoi les deux cles ne
    // doivent jamais partager la meme matiere.
    private static final byte[] USER_CODE_HMAC_KEY_MATERIAL;

    static {
        USER_CODE_HMAC_KEY_MATERIAL = new byte[32];
        for (int i = 0; i < USER_CODE_HMAC_KEY_MATERIAL.length; i++) {
            USER_CODE_HMAC_KEY_MATERIAL[i] = (byte) (255 - i);
        }
    }
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

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
    static void startDatabaseAndSeedTheBaseline() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("takibo_iam_oauth2_authz_restart")
                .withUsername("takibo")
                .withPassword("takibo");
        postgres.start();

        overrideApplicationProperties();
        migrateSchema();
        seedFirstIssuerAndBaselineClient();
    }

    @AfterAll
    static void stopDatabaseAndClearOverrides() {
        // Des proprietes systeme non nettoyees fuiraient vers toute autre classe de test
        // executee dans la meme JVM Gradle.
        for (String property : OVERRIDDEN_PROPERTIES) {
            System.clearProperty(property);
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void given_an_authorization_saved_before_the_context_closes_then_a_fresh_context_still_finds_it() {
        String accessToken;
        ConfigurableApplicationContext first = bootApplication();
        try {
            int port = Integer.parseInt(first.getEnvironment().getProperty("local.server.port"));
            HttpResponse<String> response = requestToken(port);
            assertThat(response.statusCode()).isEqualTo(200);
            accessToken = body(response).path("access_token").asText();
            assertThat(accessToken).isNotBlank();
        } finally {
            // Fermeture complete, pas une simple sortie de portee : c'est elle qui prouve
            // qu'aucun etat du premier contexte (y compris son propre bean de service) ne
            // pourrait fuiter vers le second.
            first.close();
        }

        ConfigurableApplicationContext second = bootApplication();
        try {
            OAuth2AuthorizationService authorizationService = second.getBean(OAuth2AuthorizationService.class);

            OAuth2Authorization found = authorizationService.findByToken(
                    accessToken, OAuth2TokenType.ACCESS_TOKEN);

            assertThat(found).isNotNull();
            assertThat(found.getToken(org.springframework.security.oauth2.core.OAuth2AccessToken.class)
                    .getToken().getTokenValue()).isEqualTo(accessToken);
            assertThat(found.getPrincipalName()).isEqualTo(TasBaselineDataset.SPACE_CLIENT_ID);
        } finally {
            second.close();
        }
    }

    private static HttpResponse<String> requestToken(int port) {
        String credentials = Base64.getEncoder().encodeToString(
                (TasBaselineDataset.SPACE_CLIENT_ID + ":" + TasBaselineDataset.SPACE_CLIENT_SECRET)
                        .getBytes(StandardCharsets.UTF_8));
        String form = "grant_type=client_credentials&scope="
                + URLEncoder.encode(TasBaselineDataset.SPACE_CLIENT_SCOPE, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/oauth2/token"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel /oauth2/token interrompu", e);
        } catch (Exception e) {
            throw new IllegalStateException("Appel /oauth2/token en echec", e);
        }
    }

    private static JsonNode body(HttpResponse<String> response) {
        try {
            return JSON.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("Reponse /oauth2/token illisible: " + response.body(), e);
        }
    }

    /**
     * Serveur web reel, port choisi au hasard (0) : deux contextes se suivent ici, jamais en
     * parallele, mais un port fixe resterait fragile si un autre test tournait au meme instant
     * dans la meme JVM Gradle. Le profil "test" fournit deja
     * {@code takibo.dev.postman-client.secret} ; ce test n'exerce pas ce client.
     */
    private static ConfigurableApplicationContext bootApplication() {
        return new SpringApplicationBuilder(TakiboIamBootApplication.class)
                .profiles("test")
                .run();
    }

    private static void overrideApplicationProperties() {
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgres.getUsername());
        System.setProperty("spring.datasource.password", postgres.getPassword());
        System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        System.setProperty("spring.flyway.enabled", "true");
        System.setProperty("spring.flyway.locations", "classpath:db/migration");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        // Jamais ephemere : une cle differente a chaque instance rendrait le chiffre du
        // premier contexte illisible par le second, ce que ce test doit precisement exclure.
        System.setProperty("takibo.tas.keys.ephemeral", "false");
        System.setProperty("takibo.tas.keys.cipher.active-key-id", CIPHER_KEY_ID);
        System.setProperty("takibo.tas.keys.cipher.active-key",
                Base64.getEncoder().encodeToString(CIPHER_KEY_MATERIAL));
        System.setProperty("takibo.tas.keys.user-code-hmac.key",
                Base64.getEncoder().encodeToString(USER_CODE_HMAC_KEY_MATERIAL));
        System.setProperty("management.health.mail.enabled", "false");
        System.setProperty("security.password-encoder.bcrypt-strength", "4");
        System.setProperty("server.port", "0");
    }

    private static void migrateSchema() {
        org.flywaydb.core.Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    /**
     * Amorce l'emettrice de signature (fail-closed au demarrage, comme
     * {@code SigningKeyRestartAcceptanceTest}) et le client SPACE du jeu de donnees de
     * reference, hors contexte Spring — les memes classes de production, un
     * {@code PasswordEncoder} et un {@code JdbcTemplate} bruts.
     */
    private static void seedFirstIssuerAndBaselineClient() {
        try (HikariDataSource dataSource = dataSource()) {
            SecretCipherKey cipherKey = new SecretCipherKey(CIPHER_KEY_ID, CIPHER_KEY_MATERIAL);
            SigningKeyRotationService bootstrap = new SigningKeyRotationService(
                    new RsaSigningKeyGenerator(),
                    new JdbcFirstIssuerWriter(new JdbcTemplate(dataSource)),
                    new AesGcmSecretCipher(cipherKey),
                    Clock.systemUTC());
            bootstrap.initializeFirstIssuer();

            new TasBaselineDataset(new JdbcTemplate(dataSource), new BCryptPasswordEncoder(4)).reset();
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

    /** Ecriture minimale pour l'amorcage seul ; copie de {@code SigningKeyRestartAcceptanceTest}. */
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
