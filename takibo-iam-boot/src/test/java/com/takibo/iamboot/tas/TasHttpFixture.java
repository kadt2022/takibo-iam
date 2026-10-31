package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Socle HTTP des tests de bout en bout sur PostgreSQL : une organisation, un space et un
 * administrateur local, un token humain SPACE émis par l'émetteur de production, des appels
 * HTTP réels et un nettoyage complet des organisations créées.
 */
final class TasHttpFixture {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final JdbcTemplate jdbc;
    private final HumanAccessTokenIssuer tokenIssuer;
    private final int port;

    TasHttpFixture(JdbcTemplate jdbc, HumanAccessTokenIssuer tokenIssuer, int port) {
        this.jdbc = jdbc;
        this.tokenIssuer = tokenIssuer;
        this.port = port;
    }

    /** Preuve d'exécution : le test tourne sur PostgreSQL, jamais sur H2. */
    void assertRunsOnPostgres() {
        String product = jdbc.execute((Connection c) -> c.getMetaData().getDatabaseProductName());
        assertThat(product).isEqualTo("PostgreSQL");
    }

    void organizationWithSpaceAndAdmin(UUID orgId, String orgCode, UUID spaceId, String spaceCode,
                                       UUID accountId, UUID userId) {
        jdbc.update("INSERT INTO organizations (id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')",
                orgId, orgCode, "Org " + orgCode);
        jdbc.update("INSERT INTO spaces (id, org_id, code, name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                spaceId, orgId, spaceCode, "Space " + spaceCode);
        jdbc.update("INSERT INTO accounts (id, org_id, email, display_name) VALUES (?, ?, ?, ?)",
                accountId, orgId, "admin@" + orgCode + ".test", "Admin " + orgCode);
        jdbc.update("""
                INSERT INTO users (id, org_id, space_id, account_id, username, status, user_type)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'NATIVE')
                """, userId, orgId, spaceId, accountId, "admin-" + orgCode);
    }

    String spaceToken(UUID orgId, UUID spaceId, UUID accountId, UUID userId, List<String> permissions) {
        return tokenIssuer.issue(HumanTokenRequest.spaceScoped(
                orgId, spaceId, accountId, userId, List.of(), List.of(), permissions)).accessToken();
    }

    String organizationToken(UUID orgId, UUID accountId) {
        return tokenIssuer.issue(HumanTokenRequest.organizationScoped(
                orgId, accountId, List.of(), List.of(), List.of())).accessToken();
    }

    HttpResponse<String> send(String method, String path, String token, Map<String, String> headers, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        headers.forEach(request::header);
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel " + method + " " + path + " interrompu", e);
        } catch (Exception e) {
            throw new IllegalStateException("Appel " + method + " " + path + " en echec", e);
        }
    }

    HttpResponse<String> postJson(String path, String token, String body) {
        return send("POST", path, token, Map.of("Content-Type", "application/json"), body);
    }

    static JsonNode body(HttpResponse<String> response) {
        try {
            return JSON.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("Reponse illisible: " + response.body(), e);
        }
    }

    long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    /** Supprime les organisations portant ces codes, avec tout ce qui les référence. */
    void purgeOrganizations(String... codes) {
        for (String code : codes) {
            jdbc.queryForList("SELECT id FROM organizations WHERE code = ?", UUID.class, code)
                    .forEach(this::purgeOrganization);
        }
    }

    /**
     * Les tables portant {@code org_id} sont liées par des clés étrangères qui imposent un ordre.
     * Chaque passe supprime ce qu'elle peut ; une ligne encore référencée attend la passe
     * suivante. Le nettoyage échoue s'il ne converge pas.
     */
    private void purgeOrganization(UUID orgId) {
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
