package com.takibo.iamboot.tas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prouve, sur PostgreSQL réel, que {@code REPEATABLE READ} tient sa promesse pour les tables
 * que {@code JpaResolvedOAuthClientResolver} lit (TAS-GRANTS-01) : une transaction qui a déjà
 * lu une table ne voit pas une ligne insérée et validée par une transaction concurrente
 * pendant qu'elle reste ouverte, tant qu'elle n'a pas elle-même validé.
 * <p>
 * Ce test ne passe pas par le résolveur lui-même — il n'existe aucun moyen propre de mettre en
 * pause son exécution entre deux lectures sans instrumenter le code de production pour les
 * seuls besoins du test. Il reproduit à la place, avec les mêmes tables et le même niveau
 * d'isolation, exactement la garantie dont la résolution dépend. Que {@code resolve(...)}
 * porte bien cette annotation est vérifié à part, par
 * {@code JpaResolvedOAuthClientResolverTest} (réflexion, sans base).
 */
@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "security.password-encoder.bcrypt-strength=4"
})
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class OAuth2ClientSnapshotConsistencyIntegrationTest extends TasPostgresBaseline {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID clientTableId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM oauth2_client_scopes WHERE org_id = ?", TasBaselineDataset.ORG_ID);
        jdbc.update("DELETE FROM oauth2_client_grant_types WHERE org_id = ?", TasBaselineDataset.ORG_ID);
        jdbc.update("DELETE FROM oauth2_clients WHERE org_id = ?", TasBaselineDataset.ORG_ID);
        new TasBaselineDataset(jdbc, passwordEncoder).reset();

        clientTableId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO oauth2_clients (
                    id, org_id, space_id, client_id, client_name, client_type,
                    require_client_secret, client_secret_hash, token_endpoint_auth_method)
                VALUES (?, ?, ?, 'snapshot-client', 'Snapshot Client', 'CONFIDENTIAL',
                        TRUE, 'hash', 'client_secret_basic')
                """, clientTableId, TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID);
        jdbc.update("""
                INSERT INTO oauth2_client_scopes (id, org_id, space_id, client_id, scope)
                VALUES (?, ?, ?, ?, 'scope-a')
                """, UUID.randomUUID(), TasBaselineDataset.ORG_ID, TasBaselineDataset.SPACE_ID,
                clientTableId);
    }

    @Test
    void given_a_scope_committed_after_the_snapshot_started_then_the_open_transaction_never_sees_it() {
        TransactionTemplate repeatableRead = new TransactionTemplate(transactionManager);
        repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        repeatableRead.setReadOnly(true);

        List<String> secondReadScopes = repeatableRead.execute(status -> {
            // Premiere lecture : etablit l'instantane REPEATABLE READ de cette transaction.
            List<String> firstRead = jdbc.queryForList(
                    "SELECT scope FROM oauth2_client_scopes WHERE client_id = ?",
                    String.class, clientTableId);
            assertThat(firstRead).containsExactly("scope-a");

            // Une autre transaction, independante et auto-validee sur sa propre connexion,
            // ajoute un scope pendant que celle-ci reste ouverte.
            insertScopeOnASeparateConnection("scope-b");

            // Seconde lecture, meme transaction : ne doit pas voir "scope-b".
            return jdbc.queryForList(
                    "SELECT scope FROM oauth2_client_scopes WHERE client_id = ?",
                    String.class, clientTableId);
        });

        assertThat(secondReadScopes).containsExactly("scope-a");

        // Preuve que "scope-b" a bien ete valide entre-temps : une lecture hors de toute
        // transaction ouverte le voit desormais.
        List<String> afterCommit = jdbc.queryForList(
                "SELECT scope FROM oauth2_client_scopes WHERE client_id = ?",
                String.class, clientTableId);
        assertThat(afterCommit).containsExactlyInAnyOrder("scope-a", "scope-b");
    }

    /**
     * Connexion JDBC distincte de celle liee a la transaction Spring en cours sur ce thread :
     * {@code jdbc.getDataSource().getConnection()} interroge directement le pool, sans passer
     * par {@code DataSourceUtils}, donc sans reutiliser la connexion que la transaction
     * {@code REPEATABLE READ} du test retient. C'est ce qui rend les deux transactions
     * reellement concurrentes du point de vue de PostgreSQL, pas seulement du code Java.
     */
    private void insertScopeOnASeparateConnection(String scope) {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(true);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO oauth2_client_scopes (id, org_id, space_id, client_id, scope)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, TasBaselineDataset.ORG_ID);
                statement.setObject(3, TasBaselineDataset.SPACE_ID);
                statement.setObject(4, clientTableId);
                statement.setString(5, scope);
                statement.executeUpdate();
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("SNAPSHOT_TEST_CONCURRENT_INSERT_FAILED", e);
        }
    }
}
