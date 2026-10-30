package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S1-A : la création d'utilisateur par {@code accountId} ne doit rien révéler d'un compte
 * d'une autre organisation.
 * <p>
 * Chaîne réelle de bout en bout sur PostgreSQL (migrations Flyway) : token humain SPACE émis
 * par l'émetteur de production, validation, filtres, politique, contrôleur lisible, services
 * et dépôts. Rien n'est doublé.
 * <p>
 * Jeu isolé : l'organisation A porte le space de l'administrateur ; l'organisation B porte un
 * compte. L'administrateur de A soumet successivement l'{@code accountId} de ce compte et un
 * {@code accountId} qui n'existe nulle part. Les deux réponses doivent être identiques, au seul
 * identifiant soumis près, et aucune ne doit porter d'information issue de B.
 * <p>
 * Le dépôt réel est interrogé directement pour la recherche limitée à l'organisation, et une
 * inscription réelle prouve que le fondateur reste rattaché au compte qu'elle crée.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class ReadableUserCreationAccountBoundaryPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID ORG_A = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000e1");
    private static final UUID ORG_B = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000e2");
    private static final UUID SPACE_A = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000e1");
    private static final UUID ADMIN_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000e1");
    private static final UUID FOREIGN_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000e2");
    private static final UUID UNKNOWN_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000ef");
    private static final UUID ADMIN_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000e1");

    private static final String ORG_A_CODE = "s1a-org-a";
    private static final String ORG_B_CODE = "s1a-org-b";
    private static final String SPACE_A_CODE = "s1a-space-a";
    private static final String FOREIGN_EMAIL = "foreign@s1a.test";
    private static final String FOUNDER_ORG_CODE = "s1a-founder-org";

    private static final String USERS = "/api/v1/orgs/" + ORG_A_CODE + "/spaces/" + SPACE_A_CODE + "/users";
    private static final String SUBMITTED_ID = "<accountId>";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;
    @Autowired private AccountRepository accountRepository;

    private String adminToken;

    @BeforeEach
    void seed() throws Exception {
        // Preuve d'exécution : chaque test de cette classe tourne sur PostgreSQL, jamais sur H2.
        String product = jdbc.execute((java.sql.Connection c) -> {
            DatabaseMetaData meta = c.getMetaData();
            return meta.getDatabaseProductName();
        });
        assertThat(product).isEqualTo("PostgreSQL");

        purge();
        organization(ORG_A, ORG_A_CODE);
        organization(ORG_B, ORG_B_CODE);
        jdbc.update("""
                INSERT INTO spaces (id, org_id, code, name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, SPACE_A, ORG_A, SPACE_A_CODE, "S1-A Space");
        account(ADMIN_ACCOUNT, ORG_A, "admin@s1a.test");
        account(FOREIGN_ACCOUNT, ORG_B, FOREIGN_EMAIL);
        jdbc.update("""
                INSERT INTO users (id, org_id, space_id, account_id, username, status, user_type)
                VALUES (?, ?, ?, ?, 's1a-admin', 'ACTIVE', 'HUMAN')
                """, ADMIN_USER, ORG_A, SPACE_A, ADMIN_ACCOUNT);

        adminToken = tokenIssuer.issue(HumanTokenRequest.spaceScoped(
                ORG_A, SPACE_A, ADMIN_ACCOUNT, ADMIN_USER,
                List.of(), List.of(),
                List.of(TechnicalPermission.SPACE_USERS_MANAGE.code()))).accessToken();
    }

    @AfterEach
    void cleanUp() {
        purge();
    }

    @Test
    void given_an_account_of_another_organization_when_creating_a_user_then_the_response_equals_the_unknown_account_response() {
        HttpResponse<String> foreign = createUser(FOREIGN_ACCOUNT);
        HttpResponse<String> unknown = createUser(UNKNOWN_ACCOUNT);

        assertThat(foreign.statusCode())
                .as("statut : compte de B=%s / compte inexistant=%s", foreign.body(), unknown.body())
                .isEqualTo(unknown.statusCode());
        assertThat(normalized(foreign, FOREIGN_ACCOUNT))
                .as("réponse brute, compte de B : %s%nréponse brute, compte inexistant : %s",
                        foreign.body(), unknown.body())
                .isEqualTo(normalized(unknown, UNKNOWN_ACCOUNT));
    }

    @Test
    void given_an_account_of_another_organization_when_creating_a_user_then_nothing_about_that_organization_is_disclosed() {
        HttpResponse<String> foreign = createUser(FOREIGN_ACCOUNT);

        assertThat(foreign.body())
                .as("réponse brute : %s", foreign.body())
                .doesNotContain(ORG_B.toString())
                .doesNotContain(ORG_B_CODE)
                .doesNotContain(FOREIGN_EMAIL);
    }

    @Test
    void given_an_account_of_another_organization_when_creating_a_user_then_the_public_contract_is_unchanged() {
        HttpResponse<String> foreign = createUser(FOREIGN_ACCOUNT);

        assertThat(foreign.statusCode()).as("réponse brute : %s", foreign.body()).isEqualTo(400);
        assertThat(body(foreign).path("code").asText()).isEqualTo("USER_CREATION_ERROR");
    }

    @Test
    void given_a_foreign_or_unknown_account_when_creating_a_user_then_nothing_is_written() {
        long accountsBefore = count("SELECT COUNT(*) FROM accounts WHERE org_id IN (?, ?)", ORG_A, ORG_B);

        createUser(FOREIGN_ACCOUNT);
        createUser(UNKNOWN_ACCOUNT);

        assertThat(count("SELECT COUNT(*) FROM users WHERE space_id = ?", SPACE_A)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM users WHERE account_id IN (?, ?)", FOREIGN_ACCOUNT, UNKNOWN_ACCOUNT))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM takibo_identities WHERE account_id IN (?, ?)",
                FOREIGN_ACCOUNT, UNKNOWN_ACCOUNT)).isZero();
        assertThat(count("SELECT COUNT(*) FROM accounts WHERE org_id IN (?, ?)", ORG_A, ORG_B))
                .isEqualTo(accountsBefore);
    }

    // ---------- Dépôt réel : recherche limitée à l'organisation ----------

    @Test
    void given_an_account_when_it_is_looked_up_in_its_organization_then_it_is_found() {
        assertThat(accountRepository.findById(OrganizationId.of(ORG_B), AccountId.of(FOREIGN_ACCOUNT)))
                .get()
                .satisfies(account -> assertThat(account.getOrgId()).isEqualTo(ORG_B));
    }

    @Test
    void given_an_account_when_it_is_looked_up_in_another_organization_then_it_is_not_found() {
        assertThat(accountRepository.findById(OrganizationId.of(ORG_A), AccountId.of(FOREIGN_ACCOUNT))).isEmpty();
        assertThat(accountRepository.findById(OrganizationId.of(ORG_A), AccountId.of(UNKNOWN_ACCOUNT))).isEmpty();
    }

    // ---------- Non-régression : le fondateur est rattaché au compte créé par l'inscription ----------

    @Test
    void given_an_organization_signup_when_the_founder_is_provisioned_then_it_is_attached_to_the_new_account() {
        String signupToken = tokenIssuer.issue(HumanTokenRequest.organizationScoped(
                ORG_A, ADMIN_ACCOUNT, List.of(), List.of(), List.of())).accessToken();
        String payload = """
                {"organization":{"code":"%s","name":"S1-A Founder"},
                 "space":{"code":"s1a-founder-space","name":"Founder Space"},
                 "account":{"email":"founder@s1a.test","password":"Founder!Pass1"},
                 "profile":{"username":"s1a-founder","firstName":"S1","lastName":"Founder"}}"""
                .formatted(FOUNDER_ORG_CODE);

        HttpResponse<String> response = post("/api/v1/orgs/signup", signupToken, payload);

        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(201);
        JsonNode created = body(response);
        UUID organizationId = UUID.fromString(created.path("organizationId").asText());
        UUID accountId = UUID.fromString(created.path("accountId").asText());
        UUID userId = UUID.fromString(created.path("userId").asText());
        UUID spaceId = UUID.fromString(created.path("spaceId").asText());

        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ? AND account_id = ? AND space_id = ? AND org_id = ?",
                userId, accountId, spaceId, organizationId)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM accounts WHERE id = ? AND org_id = ?", accountId, organizationId))
                .isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM takibo_identities WHERE account_id = ? AND org_id = ?",
                accountId, organizationId)).isEqualTo(1L);
    }

    /**
     * Retire {@code timestamp} et {@code traceId}, seuls champs intrinsèquement variables de
     * {@code SentinelResponse}, et remplace dans le message le seul identifiant soumis par
     * l'appelant. Tout le reste est comparé tel quel : statut, code, message, chemin.
     */
    private static JsonNode normalized(HttpResponse<String> response, UUID submitted) {
        JsonNode body = body(response);
        assertThat(body.isObject()).as("réponse brute : %s", response.body()).isTrue();
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.has("traceId")).isTrue();
        assertThat(body.has("code")).isTrue();
        assertThat(body.has("message")).isTrue();

        ObjectNode copy = ((ObjectNode) body).deepCopy();
        copy.remove("timestamp");
        copy.remove("traceId");
        copy.put("message", copy.path("message").asText().replace(submitted.toString(), SUBMITTED_ID));
        return copy;
    }

    private HttpResponse<String> createUser(UUID accountId) {
        return post(USERS, adminToken, """
                {"accountId":"%s","username":"s1a-new","firstName":"S1","lastName":"A"}"""
                .formatted(accountId));
    }

    private HttpResponse<String> post(String path, String token, String payload) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel " + path + " interrompu", e);
        } catch (Exception e) {
            throw new IllegalStateException("Appel " + path + " en echec", e);
        }
    }

    private static JsonNode body(HttpResponse<String> response) {
        try {
            return JSON.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("Reponse illisible: " + response.body(), e);
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private void organization(UUID id, String code) {
        jdbc.update("""
                INSERT INTO organizations (id, code, name, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """, id, code, "S1-A " + code);
    }

    private void account(UUID id, UUID orgId, String email) {
        jdbc.update("""
                INSERT INTO accounts (id, org_id, email, display_name)
                VALUES (?, ?, ?, ?)
                """, id, orgId, email, email);
    }

    private void purge() {
        purgeSignupOrganization();
        jdbc.update("DELETE FROM takibo_identities WHERE org_id IN (?, ?)", ORG_A, ORG_B);
        jdbc.update("DELETE FROM users WHERE org_id IN (?, ?)", ORG_A, ORG_B);
        jdbc.update("DELETE FROM spaces WHERE org_id IN (?, ?)", ORG_A, ORG_B);
        jdbc.update("DELETE FROM accounts WHERE org_id IN (?, ?)", ORG_A, ORG_B);
        jdbc.update("DELETE FROM organizations WHERE id IN (?, ?)", ORG_A, ORG_B);
    }

    /**
     * L'inscription écrit dans de nombreuses tables portant {@code org_id}, dont les clés
     * étrangères imposent un ordre. Chaque passe supprime ce qu'elle peut ; une ligne encore
     * référencée attend la passe suivante. Le nettoyage échoue s'il ne converge pas.
     */
    private void purgeSignupOrganization() {
        List<UUID> ids = jdbc.queryForList("SELECT id FROM organizations WHERE code = ?", UUID.class, FOUNDER_ORG_CODE);
        if (ids.isEmpty()) {
            return;
        }
        UUID orgId = ids.getFirst();
        List<String> tables = jdbc.queryForList("""
                SELECT c.table_name FROM information_schema.columns c
                JOIN information_schema.tables t
                  ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                WHERE c.table_schema = current_schema() AND c.column_name = 'org_id'
                  AND t.table_type = 'BASE TABLE' AND c.table_name <> 'organizations'""", String.class);
        for (int pass = 0; pass < tables.size() + 1; pass++) {
            boolean remaining = false;
            for (String table : tables) {
                try {
                    // org_id n'est pas UUID partout (journal d'audit) : comparaison textuelle.
                    jdbc.update("DELETE FROM " + table + " WHERE org_id::text = ?", orgId.toString());
                } catch (DataIntegrityViolationException stillReferenced) {
                    remaining = true;
                }
            }
            if (!remaining) {
                jdbc.update("DELETE FROM organizations WHERE id = ?", orgId);
                return;
            }
        }
        throw new IllegalStateException("Nettoyage de l'organisation " + orgId + " sans convergence");
    }
}
