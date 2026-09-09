package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.takibo.authorizationserver.domain.keys.SigningKeyRotationService;
import com.takibo.authorizationserver.domain.keys.model.GeneratedSigningKeyMaterial;
import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyMaterialGenerator;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** Secret du client PLATFORM in-memory, tel que le fixe application-test.yml. */
    private static final String PLATFORM_CLIENT_SECRET = "test-ci-secret-placeholder";

    /** Parametres prives d'un JWK, RFC 7517 et RFC 7518. Aucun ne doit sortir. */
    private static final List<String> PRIVATE_JWK_PARAMETERS =
            List.of("d", "p", "q", "dp", "dq", "qi", "oth", "k");

    /**
     * Matiere privee <b>en clair</b> de la cle amorcee, retenue a la generation.
     * <p>
     * Sans elle, le test de non-fuite ne chercherait que la cle de chiffrement au repos, qui
     * est un secret <i>different</i> : une trace qui ecrirait la cle RSA privee dechiffree
     * passerait inapercue. C'est le seul endroit du depot ou cette matiere est conservee, et
     * uniquement pour pouvoir affirmer qu'elle n'apparait nulle part ailleurs.
     */
    private static String seededPrivateJwkJson;

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
        seedFirstIssuerAndBaseline();
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
     * Critere d'acceptation : « {@code client_credentials} PLATFORM et SPACE reste verifiable
     * avant et apres redemarrage ».
     * <p>
     * Le test frere {@code OAuth2AuthorizationRestartAcceptanceTest} prouve que
     * l'<i>autorisation</i> survit au redemarrage, en la retrouvant par {@code findByToken}.
     * Ce n'est pas la meme propriete : retrouver une ligne en base n'exige aucune cle. Ici
     * c'est la <b>signature</b> qui est verifiee par le second contexte, donc la cle privee
     * rechargee depuis la base.
     * <p>
     * Les deux portees sont exercees parce qu'elles empruntent deux resolutions de client
     * distinctes : le client SPACE vient de la base, le client PLATFORM est declare in-memory.
     * Une regression qui ne toucherait qu'une des deux passerait inapercue si une seule
     * etait testee.
     */
    @Test
    void given_client_credentials_tokens_issued_before_the_restart_then_a_fresh_context_still_verifies_them() {
        String spaceToken;
        String platformToken;
        ConfigurableApplicationContext first = bootApplication();
        try {
            int port = localPort(first);
            spaceToken = clientCredentialsToken(port,
                    TasBaselineDataset.SPACE_CLIENT_ID,
                    TasBaselineDataset.SPACE_CLIENT_SECRET,
                    TasBaselineDataset.SPACE_CLIENT_SCOPE);
            platformToken = clientCredentialsToken(port,
                    TasBaselineDataset.PLATFORM_CLIENT_ID,
                    PLATFORM_CLIENT_SECRET,
                    null);
        } finally {
            first.close();
        }

        ConfigurableApplicationContext second = bootApplication();
        try {
            JwtDecoder decoder = second.getBean(JwtDecoder.class);

            Jwt space = decoder.decode(spaceToken);
            Jwt platform = decoder.decode(platformToken);

            assertThat(space.getSubject()).isEqualTo(TasBaselineDataset.SPACE_CLIENT_ID);
            assertThat(platform.getSubject()).isEqualTo(TasBaselineDataset.PLATFORM_CLIENT_ID);
        } finally {
            second.close();
        }
    }

    /**
     * Critere d'acceptation : « le parcours humain {@code /api/v1/auth/login} reste verifiable
     * avant et apres redemarrage ; les tokens humains et machine partagent la meme cle,
     * propriete que ce recit ne doit pas rompre ».
     * <p>
     * Deux assertions distinctes, parce que ce sont deux risques distincts. La survie du token
     * humain au redemarrage se prouve en le decodant avec le second contexte. Le partage de
     * cle se prouve en comparant le {@code kid} de l'en-tete des deux tokens : une
     * implementation qui se mettrait a signer les humains avec une seconde cle passerait la
     * premiere assertion sans probleme.
     */
    @Test
    void given_a_human_login_token_issued_before_the_restart_then_it_survives_and_shares_the_machine_key() {
        String humanToken;
        String machineToken;
        ConfigurableApplicationContext first = bootApplication();
        try {
            int port = localPort(first);
            humanToken = humanLoginToken(port);
            machineToken = clientCredentialsToken(port,
                    TasBaselineDataset.SPACE_CLIENT_ID,
                    TasBaselineDataset.SPACE_CLIENT_SECRET,
                    TasBaselineDataset.SPACE_CLIENT_SCOPE);
        } finally {
            first.close();
        }

        ConfigurableApplicationContext second = bootApplication();
        try {
            JwtDecoder decoder = second.getBean(JwtDecoder.class);

            Jwt human = decoder.decode(humanToken);

            assertThat(human.getSubject()).isEqualTo(TasBaselineDataset.ACCOUNT_ID.toString());
            assertThat(human.getClaimAsString("subject_type")).isEqualTo("HUMAN");
            assertThat(kidOf(humanToken))
                    .as("les tokens humains et machine doivent etre signes par la meme cle")
                    .isEqualTo(kidOf(machineToken));
        } finally {
            second.close();
        }
    }

    /**
     * Critere d'acceptation : « une cle privee n'apparait jamais dans le JWKS, les logs, les
     * metriques ou une erreur ».
     * <p>
     * {@code JwkSetEndpointIntegrationTest} couvre deja la moitie JWKS, mais sur la source
     * ephemere du profil de test. Ici la cle est persistante et chiffree au repos, donc deux
     * materiaux distincts peuvent fuir : la cle RSA privee elle-meme, et la cle de chiffrement
     * qui la protege.
     * <p>
     * Les surfaces sont exercees separement. Le JWKS est lu et fouille. La configuration et
     * les metriques sont d'abord verifiees <b>fermees</b> — fouiller un corps de refus ne
     * prouverait rien si la surface etait en realite ouverte — puis fouillees quand meme,
     * parce qu'un refus mal ecrit peut lui aussi laisser fuir. Les logs sont captures pendant
     * le demarrage et pendant une emission reelle, la ou une trace mal placee ecrirait la
     * matiere.
     */
    @Test
    void given_a_persistent_signing_key_then_no_private_material_leaks_through_any_public_surface() {
        String cipherKeyBase64 = Base64.getEncoder().encodeToString(CIPHER_KEY_MATERIAL);
        // Deux secrets distincts, donc deux jeux de valeurs recherchees. Ne chercher que la
        // cle de chiffrement laisserait passer une trace qui ecrirait la cle RSA dechiffree.
        List<String> signingMaterial = privateSigningMaterialFragments();
        assertThat(signingMaterial)
                .as("la matiere privee de la cle amorcee doit avoir ete retenue")
                .isNotEmpty();

        try (StreamCapture logs = new StreamCapture()) {
            ConfigurableApplicationContext context = bootApplication();
            try {
                int port = localPort(context);

                String jwks = get(port, "/oauth2/jwks").body();
                JsonNode keys = readJson(jwks).path("keys");
                assertThat(keys).isNotEmpty();
                for (JsonNode key : keys) {
                    for (String parameter : PRIVATE_JWK_PARAMETERS) {
                        assertThat(key.has(parameter))
                                .as("le JWKS ne doit jamais exposer le parametre prive '%s'", parameter)
                                .isFalse();
                    }
                }
                assertThat(jwks)
                        .as("le JWKS ne doit pas non plus laisser fuir la cle de chiffrement au repos")
                        .doesNotContain(cipherKeyBase64);
                for (String fragment : signingMaterial) {
                    assertThat(jwks)
                            .as("le JWKS ne doit contenir aucune composante privee de la cle de signature")
                            .doesNotContain(fragment);
                }

                // Une emission reelle : c'est le moment ou la cle privee est dechiffree et
                // utilisee, donc le moment ou une trace la ferait fuir.
                clientCredentialsToken(port,
                        TasBaselineDataset.SPACE_CLIENT_ID,
                        TasBaselineDataset.SPACE_CLIENT_SECRET,
                        TasBaselineDataset.SPACE_CLIENT_SCOPE);

                for (String path : new String[]{"/actuator/env", "/actuator/metrics"}) {
                    HttpResponse<String> response = get(port, path);

                    assertThat(response.statusCode())
                            .as("%s doit rester ferme a un appelant anonyme (SEC-TMS-03)", path)
                            .isEqualTo(401);
                    assertThat(response.body())
                            .as("meme un refus ne doit rien reveler sur %s", path)
                            .doesNotContain(cipherKeyBase64);
                    for (String fragment : signingMaterial) {
                        assertThat(response.body())
                                .as("aucune composante privee de la cle de signature sur %s", path)
                                .doesNotContain(fragment);
                    }
                }

                // Garde-fou : sans lui, une capture vide ferait passer l'assertion suivante
                // sans rien verifier. C'est exactement ce qui s'est produit avec un
                // ListAppender Logback, que Spring Boot detache en reconfigurant le logger.
                String captured = logs.captured();
                assertThat(captured)
                        .as("la capture de flux doit avoir vu passer les traces du demarrage")
                        .isNotBlank();
                assertThat(captured)
                        .as("la cle de chiffrement au repos ne doit pas apparaitre dans les logs")
                        .doesNotContain(cipherKeyBase64);
                for (String fragment : signingMaterial) {
                    assertThat(captured)
                            .as("aucune composante privee de la cle de signature dans les logs")
                            .doesNotContain(fragment);
                }
            } finally {
                context.close();
            }
        }
    }

    private static int localPort(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    }

    /** En-tete JOSE du JWT, decodee sans verification : seul le {@code kid} est lu ici. */
    private static String kidOf(String token) {
        String header = new String(
                Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))),
                StandardCharsets.UTF_8);
        return readJson(header).path("kid").asText();
    }

    private static String clientCredentialsToken(int port, String clientId, String secret, String scope) {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        String form = "grant_type=client_credentials"
                + (scope == null ? "" : "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8));
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/oauth2/token"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build());

        assertThat(response.statusCode()).as("POST /oauth2/token pour %s", clientId).isEqualTo(200);
        String token = readJson(response.body()).path("access_token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private static String humanLoginToken(int port) {
        String payload = "{\"orgCode\":\"" + TasBaselineDataset.ORG_CODE
                + "\",\"email\":\"" + TasBaselineDataset.ACCOUNT_EMAIL
                + "\",\"password\":\"" + TasBaselineDataset.ACCOUNT_PASSWORD + "\"}";
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build());

        assertThat(response.statusCode()).as("POST /api/v1/auth/login").isEqualTo(200);
        String token = readJson(response.body()).path("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private static HttpResponse<String> get(int port, String path) {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build());
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel " + request.uri() + " interrompu", e);
        } catch (Exception e) {
            throw new IllegalStateException("Appel " + request.uri() + " en echec", e);
        }
    }

    private static JsonNode readJson(String body) {
        try {
            return JSON.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Reponse illisible: " + body, e);
        }
    }

    /**
     * Capture de {@code System.out} et {@code System.err}.
     * <p>
     * Un {@code ListAppender} Logback ne convient pas ici, et l'essai l'a prouve : Spring Boot
     * reconfigure Logback pendant le demarrage et detache tout appendeur pose avant, si bien
     * que la capture restait vide et que l'assertion « aucun secret dans les logs » passait
     * sans rien verifier. Les flux, eux, survivent : le {@code ConsoleAppender} reconfigure
     * ecrit dans le {@code System.out} courant, donc dans ce tampon.
     * <p>
     * Les flux d'origine restent branches en parallele, pour ne pas rendre muet le rapport de
     * test en cas d'echec.
     */
    private static final class StreamCapture implements AutoCloseable {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final PrintStream originalOut = System.out;
        private final PrintStream originalErr = System.err;

        StreamCapture() {
            System.setOut(tee(originalOut));
            System.setErr(tee(originalErr));
        }

        private PrintStream tee(PrintStream original) {
            return new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    original.write(b);
                    buffer.write(b);
                }

                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    original.write(bytes, offset, length);
                    buffer.write(bytes, offset, length);
                }
            }, true, StandardCharsets.UTF_8);
        }

        String captured() {
            System.out.flush();
            System.err.flush();
            return buffer.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            System.setOut(originalOut);
            System.setErr(originalErr);
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
     * <p>
     * Le jeu de donnees de reference suit, parce que les preuves de survie du
     * {@code client_credentials} et du login humain passent par de vraies routes : sans
     * organisation, sans compte et sans client, elles n'auraient rien a emettre.
     */
    private static void seedFirstIssuerAndBaseline() {
        try (HikariDataSource dataSource = dataSource()) {
            SecretCipherKey cipherKey = new SecretCipherKey(CIPHER_KEY_ID, CIPHER_KEY_MATERIAL);
            SigningKeyRotationService bootstrap = new SigningKeyRotationService(
                    recordingGenerator(),
                    new JdbcFirstIssuerWriter(new JdbcTemplate(dataSource)),
                    new AesGcmSecretCipher(cipherKey),
                    Clock.systemUTC());

            bootstrap.initializeFirstIssuer();

            new TasBaselineDataset(new JdbcTemplate(dataSource), new BCryptPasswordEncoder(4)).reset();
        }
    }

    /**
     * Le generateur RSA de production, qui retient au passage la matiere privee en clair.
     * <p>
     * Elle ne traverse pas la couche de persistance — {@code SigningKeyRotationService} la
     * chiffre avant l'ecriture — donc la relire depuis la base imposerait de dechiffrer, et
     * donc de deviner le {@code SecretContext} employe. L'intercepter a la source est plus
     * direct et plus sur : c'est exactement la valeur que le test doit ne pas retrouver.
     */
    private static SigningKeyMaterialGenerator recordingGenerator() {
        RsaSigningKeyGenerator delegate = new RsaSigningKeyGenerator();
        return () -> {
            GeneratedSigningKeyMaterial material = delegate.generate();
            seededPrivateJwkJson = material.privateKeyMaterial();
            return material;
        };
    }

    /**
     * Composantes privees du JWK RSA amorce : {@code d} et les facteurs du theoreme des
     * restes chinois. Chacune est une chaine base64url longue, donc une valeur de recherche
     * discriminante. Les composantes publiques ({@code n}, {@code e}, {@code kid}) sont
     * volontairement exclues : elles sont censees apparaitre dans le JWKS.
     */
    private static List<String> privateSigningMaterialFragments() {
        JsonNode jwk = readJson(seededPrivateJwkJson);
        List<String> fragments = new java.util.ArrayList<>();
        for (String member : List.of("d", "p", "q", "dp", "dq", "qi")) {
            String value = jwk.path(member).asText();
            if (!value.isBlank()) {
                fragments.add(value);
            }
        }
        return List.copyOf(fragments);
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

        /**
         * Sans concurrence dans ce test, l'amorcage tolerant a la course se ramene a
         * l'amorcage simple ; l'arbitrage reel par la base est prouve dans
         * {@code SigningKeyBootstrapIntegrationTest}.
         */
        @Override
        public boolean tryActivateFirstIssuer(NewSigningKey candidate) {
            activateFirstIssuer(candidate);
            return true;
        }

        private static String toJson(Map<String, Object> publicJwkJson) {
            return JSONObjectUtils.toJSONString(publicJwkJson);
        }
    }
}
