package com.takibo.iamboot.tas;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.infrastructure.springauthserver.client.JpaResolvedOAuthClientResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Les trois frontières du registre OAuth, de bout en bout sur PostgreSQL réel
 * (TMS-OAUTH-CLIENT-BOUNDARY-01).
 * <p>
 * Chaque scénario écrit un client <b>avec sa configuration</b>, puis le relit par le
 * résolveur de production. Prouver la seule ligne parente ne prouverait rien d'utilisable :
 * {@code JpaResolvedOAuthClientResolver} traite un client sans grant type comme introuvable,
 * si bien qu'un client PLATFORM ou ORGANIZATION représentable mais incapable de porter sa
 * configuration serait un progrès purement théorique.
 * <p>
 * Le cœur de la manœuvre est le refus d'une ligne de configuration orpheline : il montre que
 * l'intégrité est <b>gagnée</b> en supprimant la frontière dupliquée des tables filles.
 * L'ancienne clé étrangère composite ne l'aurait pas refusée dès qu'une de ses colonnes était
 * nulle.
 */
@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "security.password-encoder.bcrypt-strength=4"
})
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class OAuth2ClientBoundaryIntegrationTest extends TasPostgresBaseline {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JpaResolvedOAuthClientResolver resolver;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM oauth2_clients WHERE org_id = ?", TasBaselineDataset.ORG_ID);
        // Les clients sans tenant échappent au nettoyage par organisation du jeu de référence :
        // sans cette ligne, un client PLATFORM laissé par un test précédent ferait échouer
        // l'unicité globale de client_id au test suivant.
        jdbc.update("DELETE FROM oauth2_clients WHERE org_id IS NULL");
        new TasBaselineDataset(jdbc, passwordEncoder).reset();
    }

    // ─────────────────────────────────────────────────────────────
    // Les trois frontières
    // ─────────────────────────────────────────────────────────────

    @Test
    void given_a_platform_client_then_it_persists_carries_its_configuration_and_resolves() {
        UUID id = insertClient("platform-boundary-client", null, null);
        insertConfiguration(id);

        ResolvedOAuthClient resolved = resolve("platform-boundary-client");

        assertThat(resolved.plan()).isEqualTo(ClientPlan.PLATFORM);
        assertThat(resolved.orgId()).isNull();
        assertThat(resolved.spaceId()).isNull();
        assertThat(resolved.grantTypes()).contains("client_credentials");
        assertThat(resolved.scopes()).contains("api.read");
    }

    @Test
    void given_an_organization_client_then_it_persists_without_any_space_and_resolves() {
        // La raison d'être du récit : une organisation sans aucun Space peut porter son client.
        UUID id = insertClient("organization-boundary-client", TasBaselineDataset.ORG_ID, null);
        insertConfiguration(id);

        ResolvedOAuthClient resolved = resolve("organization-boundary-client");

        assertThat(resolved.plan()).isEqualTo(ClientPlan.ORGANIZATION);
        assertThat(resolved.orgId()).isEqualTo(TasBaselineDataset.ORG_ID);
        assertThat(resolved.spaceId()).isNull();
        assertThat(resolved.grantTypes()).contains("client_credentials");
        assertThat(resolved.scopes()).contains("api.read");
    }

    @Test
    void given_a_space_client_then_nothing_changes_for_the_existing_boundary() {
        UUID id = insertClient("space-boundary-client",
                TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID);
        insertConfiguration(id);

        ResolvedOAuthClient resolved = resolve("space-boundary-client");

        assertThat(resolved.plan()).isEqualTo(ClientPlan.SPACE);
        assertThat(resolved.orgId()).isEqualTo(TasBaselineDataset.ORG_ID);
        assertThat(resolved.spaceId()).isEqualTo(TasBaselineDataset.SPACE_ID);
        assertThat(resolved.grantTypes()).contains("client_credentials");
    }

    // ─────────────────────────────────────────────────────────────
    // Ce que la base refuse
    // ─────────────────────────────────────────────────────────────

    @Test
    void given_a_space_without_an_organization_then_the_database_refuses_the_client() {
        Throwable rejection = catchThrowable(() ->
                insertClient("impossible-boundary-client", null, TasBaselineDataset.SPACE_ID));

        assertThat(rejection)
                .as("un space sans organisation ne correspond a aucune frontiere TAKIBO")
                .isNotNull()
                .hasMessageContaining("ck_oauth2_clients_boundary");
    }

    @Test
    void given_a_space_belonging_to_another_organization_then_the_client_is_refused() {
        // La FK composite reste vérifiée quand les DEUX colonnes sont présentes : ce récit
        // n'affaiblit pas l'isolation, il l'exempte seulement quand il n'y a pas de space.
        UUID foreignOrg = insertForeignOrganization();

        Throwable rejection = catchThrowable(() ->
                insertClient("cross-boundary-client", foreignOrg, TasBaselineDataset.SPACE_ID));

        assertThat(rejection)
                .as("un client ne peut pas designer le space d'une autre organisation")
                .isNotNull();

        jdbc.update("DELETE FROM organizations WHERE id = ?", foreignOrg);
    }

    /**
     * Le point qui justifie la suppression de la frontière dupliquée dans les tables filles.
     * L'ancienne clé composite, non vérifiée dès qu'une colonne était nulle, aurait laissé
     * passer cette ligne pour un client sans tenant. La clé simple la refuse toujours.
     */
    @Test
    void given_a_configuration_row_pointing_at_no_client_then_it_is_refused() {
        UUID ghost = UUID.randomUUID();

        Throwable rejection = catchThrowable(() -> jdbc.update("""
                INSERT INTO oauth2_client_grant_types (id, client_id, grant_type)
                VALUES (?, ?, 'client_credentials')
                """, UUID.randomUUID(), ghost));

        assertThat(rejection)
                .as("aucune configuration ne peut exister sans son client")
                .isNotNull()
                .hasMessageContaining("fk_ocg_client");
    }

    @Test
    void given_a_deleted_client_then_its_configuration_goes_with_it() {
        UUID id = insertClient("cascade-boundary-client", TasBaselineDataset.ORG_ID, null);
        insertConfiguration(id);
        assertThat(configurationRowsOf(id)).isEqualTo(3);

        jdbc.update("DELETE FROM oauth2_clients WHERE id = ?", id);

        assertThat(configurationRowsOf(id))
                .as("la cascade passe par la FK simple, y compris sans tenant")
                .isZero();
    }

    // ─────────────────────────────────────────────────────────────
    // Outillage
    // ─────────────────────────────────────────────────────────────

    private ResolvedOAuthClient resolve(String clientId) {
        Optional<ResolvedOAuthClient> resolved = resolver.resolve(clientId);
        assertThat(resolved).as("client %s introuvable", clientId).isPresent();
        return resolved.orElseThrow();
    }

    private UUID insertClient(String clientId, UUID orgId, UUID spaceId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO oauth2_clients (
                    id, org_id, space_id, client_id, client_name, client_type,
                    require_client_secret, client_secret_hash, token_endpoint_auth_method)
                VALUES (?, ?, ?, ?, ?, 'CONFIDENTIAL', TRUE, 'hash', 'client_secret_basic')
                """, id, orgId, spaceId, clientId, clientId);
        return id;
    }

    /** Un grant type, un scope et une URI de redirection : de quoi rendre le client utilisable. */
    private void insertConfiguration(UUID clientId) {
        jdbc.update("""
                INSERT INTO oauth2_client_grant_types (id, client_id, grant_type)
                VALUES (?, ?, 'client_credentials')
                """, UUID.randomUUID(), clientId);
        jdbc.update("""
                INSERT INTO oauth2_client_scopes (id, client_id, scope)
                VALUES (?, ?, 'api.read')
                """, UUID.randomUUID(), clientId);
        jdbc.update("""
                INSERT INTO oauth2_client_redirect_uris (id, client_id, uri)
                VALUES (?, ?, 'https://app.example/callback')
                """, UUID.randomUUID(), clientId);
    }

    private int configurationRowsOf(UUID clientId) {
        Integer count = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM oauth2_client_grant_types WHERE client_id = ?)
                     + (SELECT COUNT(*) FROM oauth2_client_scopes      WHERE client_id = ?)
                     + (SELECT COUNT(*) FROM oauth2_client_redirect_uris WHERE client_id = ?)
                """, Integer.class, clientId, clientId, clientId);
        return count == null ? 0 : count;
    }

    private UUID insertForeignOrganization() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, status)
                VALUES (?, ?, 'Boundary Foreign Org', 'ACTIVE')
                """, id, "boundary-foreign-" + id.toString().substring(0, 8));
        return id;
    }
}
