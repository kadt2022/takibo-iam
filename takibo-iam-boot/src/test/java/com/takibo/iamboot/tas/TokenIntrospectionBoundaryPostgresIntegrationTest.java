package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import org.junit.jupiter.api.AfterEach;
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

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-HUNT-05 — ce que l'introspection révèle, et à qui.
 * <p>
 * Conclusion du récit : aucune divulgation non autorisée. Un client qui introspecte un token
 * qu'il détient déjà n'apprend que les claims lisibles dans ce JWT, plus son état
 * {@code active}. Ces tests fixent ce contrat pour qu'un changement soit visible : un champ
 * hors du token, une réponse détaillée pour un token inactif, ou une révocation croisée
 * feraient échouer la suite.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class TokenIntrospectionBoundaryPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID ORG_B = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000b5");
    private static final UUID SPACE_B = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000b5");
    private static final UUID ACCOUNT_B = UUID.fromString("cccccccc-0000-0000-0000-0000000000b5");
    private static final UUID USER_B = UUID.fromString("dddddddd-0000-0000-0000-0000000000b5");
    private static final String ORG_B_CODE = "hunt05-other-org";
    private static final UUID CLIENT_B_UUID = UUID.fromString("eeeeeeee-0000-0000-0000-0000000000b5");
    private static final String CLIENT_B = "hunt05-other-client";
    private static final UUID SHORT_CLIENT_UUID = UUID.fromString("eeeeeeee-0000-0000-0000-0000000000b6");
    private static final String SHORT_CLIENT = "hunt05-short-client";
    private static final String SECRET = "hunt05-secret";
    /** Champs d'enveloppe de RFC 7662 qui ne sont pas des claims du JWT. */
    private static final Set<String> ENVELOPE = Set.of("active", "client_id", "token_type");

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;
    @Autowired private JwtDecoder jwtDecoder;

    private TasHttpFixture fixture;
    private TasBaselineDataset dataset;

    @BeforeEach
    void twoTenantsWithMachineClients() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_B_CODE);
        dataset = new TasBaselineDataset(jdbc, passwordEncoder);
        dataset.reset();
        fixture.organizationWithSpaceAndAdmin(ORG_B, ORG_B_CODE, SPACE_B, "hunt05-other-space", ACCOUNT_B, USER_B);
        machineClient(CLIENT_B_UUID, ORG_B, SPACE_B, CLIENT_B, null);
        machineClient(SHORT_CLIENT_UUID, TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID, SHORT_CLIENT, 1);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM oauth2_clients WHERE id IN (?, ?)", CLIENT_B_UUID, SHORT_CLIENT_UUID);
        fixture.purgeOrganizations(ORG_B_CODE);
        dataset.clear();
    }

    @Test
    void the_owning_client_introspects_its_active_token() {
        String token = tokenOf(TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET);

        JsonNode response = introspect(token, TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET);

        assertThat(response.path("active").asBoolean()).isTrue();
        assertThat(response.path("org_id").asText()).isEqualTo(TasBaselineDataset.ORG_ID.toString());
    }

    @Test
    void a_client_of_another_organization_learns_nothing_beyond_the_token_it_already_holds() {
        String token = tokenOf(TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET);
        Jwt jwt = jwtDecoder.decode(token);

        JsonNode response = introspect(token, CLIENT_B, SECRET);

        assertThat(response.path("active").asBoolean()).isTrue();
        Set<String> fields = new HashSet<>();
        response.fieldNames().forEachRemaining(fields::add);
        fields.removeAll(ENVELOPE);
        assertThat(fields).as("aucun champ absent du JWT lui-même").isSubsetOf(jwt.getClaims().keySet());
        for (String claim : List.of("sub", "org_id", "space_id", "jti", "iss")) {
            assertThat(response.path(claim).asText()).as(claim).isEqualTo(String.valueOf(jwt.getClaims().get(claim)));
        }
        assertThat(response.path("client_id").asText()).isEqualTo(TasBaselineDataset.SPACE_CLIENT_ID);
    }

    @Test
    void a_client_of_another_organization_cannot_revoke_the_token() {
        String token = tokenOf(TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET);

        HttpResponse<String> revocation = post("/oauth2/revoke", "token=" + encode(token), CLIENT_B, SECRET);

        assertThat(revocation.statusCode()).as("révocation croisée : %s", revocation.body()).isEqualTo(400);
        assertThat(introspect(token, TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET)
                .path("active").asBoolean()).as("le token reste actif").isTrue();
    }

    @Test
    void unknown_revoked_expired_and_human_tokens_reveal_only_that_they_are_inactive() throws InterruptedException {
        String revoked = tokenOf(TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET);
        post("/oauth2/revoke", "token=" + encode(revoked), TasBaselineDataset.SPACE_CLIENT_ID,
                TasBaselineDataset.SPACE_CLIENT_SECRET);
        String expired = tokenOf(SHORT_CLIENT, SECRET);
        String human = fixture.spaceToken(TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                TasBaselineDataset.ACCOUNT_ID, UUID.randomUUID(), List.of("P_SPACE_USERS_READ"));
        Thread.sleep(2_100);

        for (String token : List.of("hunt05-" + UUID.randomUUID(), revoked, expired, human)) {
            assertThat(introspect(token, CLIENT_B, SECRET).toString()).isEqualTo("{\"active\":false}");
        }
    }

    @Test
    void introspection_requires_an_authenticated_client() {
        String token = tokenOf(TasBaselineDataset.SPACE_CLIENT_ID, TasBaselineDataset.SPACE_CLIENT_SECRET);

        assertThat(fixture.send("POST", "/oauth2/introspect", null,
                Map.of("Content-Type", "application/x-www-form-urlencoded"), "token=" + encode(token)).statusCode())
                .isEqualTo(401);
        assertThat(post("/oauth2/introspect", "token=" + encode(token), CLIENT_B, "wrong").statusCode()).isEqualTo(401);
    }

    // ---------- Outils ----------

    private void machineClient(UUID id, UUID orgId, UUID spaceId, String clientId, Integer accessTokenTtl) {
        jdbc.update("""
                INSERT INTO oauth2_clients (id, org_id, space_id, client_id, client_name, client_type,
                    require_client_secret, client_secret_hash, token_endpoint_auth_method, require_pkce,
                    require_consent, access_token_ttl_seconds)
                VALUES (?, ?, ?, ?, ?, 'CONFIDENTIAL', TRUE, ?, 'client_secret_basic', FALSE, FALSE, ?)
                """, id, orgId, spaceId, clientId, clientId, passwordEncoder.encode(SECRET), accessTokenTtl);
        jdbc.update("INSERT INTO oauth2_client_grant_types (id, client_id, grant_type) VALUES (?, ?, 'client_credentials')",
                UUID.randomUUID(), id);
        jdbc.update("INSERT INTO oauth2_client_scopes (id, client_id, scope) VALUES (?, ?, 'api.read')",
                UUID.randomUUID(), id);
    }

    private String tokenOf(String clientId, String secret) {
        HttpResponse<String> response = post("/oauth2/token", "grant_type=client_credentials&scope=api.read",
                clientId, secret);
        assertThat(response.statusCode()).as("token de %s : %s", clientId, response.body()).isEqualTo(200);
        return TasHttpFixture.body(response).path("access_token").asText();
    }

    private JsonNode introspect(String token, String clientId, String secret) {
        HttpResponse<String> response = post("/oauth2/introspect", "token=" + encode(token), clientId, secret);
        assertThat(response.statusCode()).as("introspection : %s", response.body()).isEqualTo(200);
        return TasHttpFixture.body(response);
    }

    private HttpResponse<String> post(String path, String form, String clientId, String secret) {
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        return fixture.send("POST", path, null, Map.of("Authorization", "Basic " + basic,
                "Content-Type", "application/x-www-form-urlencoded"), form);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
