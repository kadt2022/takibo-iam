package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baseline du parcours humain existant (TAS-GRANTS-00).
 * <p>
 * {@code POST /api/v1/auth/login} est aujourd'hui la seule surface d'authentification humaine.
 * Elle ne passe pas par Spring Authorization Server : TIS-CORE verifie l'identite, resout le
 * pouvoir effectif, puis demande la signature au port {@code HumanAccessTokenIssuer}, cable sur
 * {@code HumanTokenSigner}. Aucune autorisation OAuth2, aucun refresh token.
 * <p>
 * Le recit 03 extraira de {@code HumanLoginService} la verification d'identite pour la partager
 * avec l'authentification navigateur de SAS. Ce refactor touche la seule voie d'entree humaine
 * en production : il lui faut un filet prealable. Le voici.
 * <p>
 * Portee couverte : ORGANIZATION, le chemin canonique d'IAM 31. Le chemin SPACE est explicitement
 * transitoire ; il n'est verifie ici que par sa frontiere, qui refuse un compte sans user local.
 * <p>
 * Invariant central de la surface : toutes les causes d'echec convergent vers un 401 indistinct.
 * La cause reelle vit dans les logs et l'audit, jamais dans la reponse.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class HumanLoginBaselineIntegrationTest extends TasPostgresBaseline {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtDecoder jwtDecoder;

    @BeforeEach
    void seed() {
        new TasBaselineDataset(jdbc, passwordEncoder).reset();
    }

    // ---------- Portee ORGANIZATION : le chemin canonique ----------

    @Test
    void given_valid_credentials_when_login_then_issues_an_organization_scoped_token() {
        HttpResponse<String> response = login(
                TasBaselineDataset.ORG_CODE,
                TasBaselineDataset.ACCOUNT_EMAIL,
                TasBaselineDataset.ACCOUNT_PASSWORD,
                null);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = body(response);

        assertThat(body.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.path("scopeLevel").asText()).isEqualTo("ORGANIZATION");
        assertThat(body.path("organizationId").asText())
                .isEqualTo(TasBaselineDataset.ORG_ID.toString());
        assertThat(body.path("accountId").asText())
                .isEqualTo(TasBaselineDataset.ACCOUNT_ID.toString());
        assertThat(body.path("expiresIn").asLong()).isPositive();

        // IAM 31 : le user local est une realite de space. En portee ORGANIZATION,
        // spaceId et userId sont absents de la reponse, pas nuls.
        assertThat(body.has("spaceId")).isFalse();
        assertThat(body.has("userId")).isFalse();
    }

    @Test
    void given_valid_credentials_when_login_then_the_signed_token_situates_the_human() {
        HttpResponse<String> response = login(
                TasBaselineDataset.ORG_CODE,
                TasBaselineDataset.ACCOUNT_EMAIL,
                TasBaselineDataset.ACCOUNT_PASSWORD,
                null);

        Jwt jwt = jwtDecoder.decode(body(response).path("accessToken").asText());

        assertThat(jwt.getSubject()).isEqualTo(TasBaselineDataset.ACCOUNT_ID.toString());
        assertThat(jwt.getClaimAsString("subject_type")).isEqualTo("HUMAN");
        assertThat(jwt.getClaimAsString("auth_method")).isEqualTo("PASSWORD");
        assertThat(jwt.getClaimAsString("takibo_scope_level")).isEqualTo("ORGANIZATION");
        assertThat(jwt.getClaimAsString("takibo_tenant_source")).isEqualTo("human_login");
        assertThat(jwt.getClaimAsString("org_id")).isEqualTo(TasBaselineDataset.ORG_ID.toString());
        assertThat(jwt.getClaimAsString("account_id"))
                .isEqualTo(TasBaselineDataset.ACCOUNT_ID.toString());

        // Un token ORGANIZATION ne porte aucun space ni user local.
        assertThat(jwt.hasClaim("space_id")).isFalse();
        assertThat(jwt.hasClaim("user_id")).isFalse();
    }

    @Test
    void given_account_without_any_assignment_when_login_then_power_snapshot_is_empty() {
        // TAS signe le pouvoir effectif calcule par TIS-CORE, sans jamais le completer.
        // Un compte sans attribution recoit donc un instantane vide, present et vide.
        HttpResponse<String> response = login(
                TasBaselineDataset.ORG_CODE,
                TasBaselineDataset.ACCOUNT_EMAIL,
                TasBaselineDataset.ACCOUNT_PASSWORD,
                null);

        Jwt jwt = jwtDecoder.decode(body(response).path("accessToken").asText());

        assertThat(jwt.getClaimAsStringList("roles")).isEmpty();
        assertThat(jwt.getClaimAsStringList("groups")).isEmpty();
        assertThat(jwt.getClaimAsStringList("permissions")).isEmpty();
    }

    @Test
    void given_valid_credentials_when_login_then_the_token_is_signed_by_the_machine_token_key() {
        // Propriete de conception assumee : les tokens humains et machine partagent la meme
        // clef. Le recit 02A remplacera la source de clefs ; cette propriete doit survivre.
        HttpResponse<String> response = login(
                TasBaselineDataset.ORG_CODE,
                TasBaselineDataset.ACCOUNT_EMAIL,
                TasBaselineDataset.ACCOUNT_PASSWORD,
                null);

        Jwt jwt = jwtDecoder.decode(body(response).path("accessToken").asText());

        assertThat(jwt.getHeaders().get("kid")).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    // ---------- Refus : une seule reponse, quelle que soit la cause ----------

    @Test
    void given_wrong_password_when_login_then_undifferentiated_401() {
        assertRefused(login(
                TasBaselineDataset.ORG_CODE,
                TasBaselineDataset.ACCOUNT_EMAIL,
                "Wrong!Password9",
                null));
    }

    @Test
    void given_unknown_email_when_login_then_undifferentiated_401() {
        assertRefused(login(
                TasBaselineDataset.ORG_CODE,
                "nobody@takibo.test",
                TasBaselineDataset.ACCOUNT_PASSWORD,
                null));
    }

    @Test
    void given_unknown_organization_when_login_then_undifferentiated_401() {
        assertRefused(login(
                "NO-SUCH-ORG",
                TasBaselineDataset.ACCOUNT_EMAIL,
                TasBaselineDataset.ACCOUNT_PASSWORD,
                null));
    }

    @Test
    void given_space_scope_without_local_user_when_login_then_undifferentiated_401() {
        // Frontiere SPACE : pas de token situe sans user local dans le space demande,
        // meme quand l'organisation, le compte et le mot de passe sont valides.
        assertRefused(login(
                TasBaselineDataset.ORG_CODE,
                TasBaselineDataset.ACCOUNT_EMAIL,
                TasBaselineDataset.ACCOUNT_PASSWORD,
                TasBaselineDataset.SPACE_CODE));
    }

    @Test
    void given_every_failure_cause_when_login_then_payloads_are_byte_for_byte_identical() {
        HttpResponse<String> badPassword = login(TasBaselineDataset.ORG_CODE,
                TasBaselineDataset.ACCOUNT_EMAIL, "Wrong!Password9", null);
        HttpResponse<String> unknownAccount = login(TasBaselineDataset.ORG_CODE,
                "nobody@takibo.test", TasBaselineDataset.ACCOUNT_PASSWORD, null);
        HttpResponse<String> unknownOrg = login("NO-SUCH-ORG",
                TasBaselineDataset.ACCOUNT_EMAIL, TasBaselineDataset.ACCOUNT_PASSWORD, null);
        HttpResponse<String> noLocalUser = login(TasBaselineDataset.ORG_CODE,
                TasBaselineDataset.ACCOUNT_EMAIL, TasBaselineDataset.ACCOUNT_PASSWORD,
                TasBaselineDataset.SPACE_CODE);

        assertThat(badPassword.statusCode())
                .isEqualTo(unknownAccount.statusCode())
                .isEqualTo(unknownOrg.statusCode())
                .isEqualTo(noLocalUser.statusCode());

        // Comparaison de la charge utile entiere, et non de quelques champs choisis :
        // tout champ ajoute plus tard qui differerait selon la cause ferait tomber ce test.
        assertThat(withoutVariableFields(badPassword))
                .isEqualTo(withoutVariableFields(unknownAccount))
                .isEqualTo(withoutVariableFields(unknownOrg))
                .isEqualTo(withoutVariableFields(noLocalUser));
    }

    /**
     * Retire les deux seuls champs intrinsequement variables de {@code SentinelResponse}
     * — {@code timestamp} et {@code traceId} — et rend tout le reste tel quel.
     * <p>
     * Deux gardes encadrent cette normalisation, pour que la comparaison qui s'appuie dessus
     * ne puisse pas devenir vide sans qu'on s'en apercoive :
     * <ul>
     *   <li>les champs retires doivent exister ; renommes ou supprimes, la normalisation
     *       deviendrait un no-op silencieux ;</li>
     *   <li>les champs porteurs de cause doivent subsister. Elargir la normalisation
     *       jusqu'a {@code code} ou {@code message} reduirait la comparaison a une egalite
     *       triviale entre deux objets vides.</li>
     * </ul>
     */
    private static JsonNode withoutVariableFields(HttpResponse<String> response) {
        JsonNode body = body(response);
        assertThat(body.isObject())
                .as("la reponse de refus doit etre un objet JSON")
                .isTrue();
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.has("traceId")).isTrue();

        ObjectNode normalized = ((ObjectNode) body).deepCopy();
        normalized.remove("timestamp");
        normalized.remove("traceId");

        assertThat(normalized.has("code"))
                .as("la comparaison doit porter sur le code d'erreur")
                .isTrue();
        assertThat(normalized.has("message"))
                .as("la comparaison doit porter sur le message")
                .isTrue();
        return normalized;
    }

    private static void assertRefused(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(body(response).path("accessToken").asText()).isEmpty();
    }

    // ---------- Appel HTTP ----------

    private HttpResponse<String> login(String orgCode, String email, String password,
                                       String spaceCode) {
        StringBuilder payload = new StringBuilder("{")
                .append(quoted("email")).append(':').append(quoted(email)).append(',')
                .append(quoted("password")).append(':').append(quoted(password)).append(',')
                .append(quoted("orgCode")).append(':').append(quoted(orgCode));
        if (spaceCode != null) {
            payload.append(',').append(quoted("spaceCode")).append(':').append(quoted(spaceCode));
        }
        payload.append('}');

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel /api/v1/auth/login interrompu", e);
        } catch (Exception e) {
            throw new IllegalStateException("Appel /api/v1/auth/login en echec", e);
        }
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static JsonNode body(HttpResponse<String> response) {
        String raw = response.body();
        if (raw == null || raw.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Reponse /api/v1/auth/login illisible: " + raw, e);
        }
    }
}
