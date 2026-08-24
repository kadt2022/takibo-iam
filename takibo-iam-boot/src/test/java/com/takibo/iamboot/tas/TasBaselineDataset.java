package com.takibo.iamboot.tas;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

/**
 * Jeu de donnees minimal des tests de reference TAS (TAS-GRANTS-00).
 * <p>
 * Une organisation, un space, un compte et un client OAuth2 {@code client_credentials} :
 * le strict necessaire pour qu'un token machine SPACE soit emis et qu'un login humain
 * ORGANIZATION aboutisse. Rien de plus, pour que ce qui casse un jour designe une cause
 * unique.
 * <p>
 * Les empreintes sont produites par le {@link PasswordEncoder} reel du contexte, jamais
 * figees en dur : le secret client et le mot de passe restent valides meme si la force
 * BCrypt change. C'est la meme regle que suit {@code TakiboRegisteredClientRepository},
 * qui transmet l'empreinte stockee telle quelle a Spring Authorization Server.
 * <p>
 * Le client PLATFORM ne figure pas ici : {@code postman-client} est declare in-memory par
 * {@code InMemoryDevRegisteredClientConfiguration} et n'a, par construction, ni organisation
 * ni space. Son secret vient de {@code takibo.dev.postman-client.secret}.
 */
final class TasBaselineDataset {

    static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    static final UUID SPACE_CLIENT_UUID = UUID.fromString("dddddddd-0000-0000-0000-000000000005");

    static final String ORG_CODE = "BASELINE-ORG";
    static final String SPACE_CODE = "BASELINE-SPACE";

    static final String ACCOUNT_EMAIL = "baseline@takibo.test";
    static final String ACCOUNT_PASSWORD = "Baseline!Pass1";

    static final String SPACE_CLIENT_ID = "baseline-space-client";
    static final String SPACE_CLIENT_SECRET = "baseline-space-secret";
    static final String SPACE_CLIENT_SCOPE = "api.read";

    static final String PLATFORM_CLIENT_ID = "postman-client";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    TasBaselineDataset(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /** Reinstalle le jeu de donnees. Idempotent : rejouable entre deux tests. */
    void reset() {
        clear();
        insertOrganization();
        insertAccount();
        insertSpace();
        insertAccountCredentials();
        insertSpaceClient();
    }

    /** Supprime uniquement les lignes de ce jeu ; le referentiel RBAC migre reste intact. */
    void clear() {
        jdbc.update("DELETE FROM oauth2_client_scopes WHERE org_id = ?", ORG_ID);
        jdbc.update("DELETE FROM oauth2_client_grant_types WHERE org_id = ?", ORG_ID);
        jdbc.update("DELETE FROM oauth2_authorization WHERE org_id = ?", ORG_ID);
        jdbc.update("DELETE FROM oauth2_clients WHERE org_id = ?", ORG_ID);
        jdbc.update("DELETE FROM account_credentials WHERE org_id = ?", ORG_ID);
        jdbc.update("DELETE FROM spaces WHERE org_id = ?", ORG_ID);
        jdbc.update("DELETE FROM accounts WHERE org_id = ?", ORG_ID);
        jdbc.update("DELETE FROM organizations WHERE id = ?", ORG_ID);
    }

    long countAuthorizationRows() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization", Long.class);
        return count == null ? 0L : count;
    }

    private void insertOrganization() {
        jdbc.update("""
                INSERT INTO organizations (id, code, name, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """, ORG_ID, ORG_CODE, "Baseline Organization");
    }

    private void insertAccount() {
        jdbc.update("""
                INSERT INTO accounts (id, org_id, email, display_name)
                VALUES (?, ?, ?, ?)
                """, ACCOUNT_ID, ORG_ID, ACCOUNT_EMAIL, "Baseline Account");
    }

    private void insertSpace() {
        jdbc.update("""
                INSERT INTO spaces (id, org_id, code, name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, SPACE_ID, ORG_ID, SPACE_CODE, "Baseline Space");
    }

    private void insertAccountCredentials() {
        jdbc.update("""
                INSERT INTO account_credentials (org_id, account_id, password_hash, password_algo)
                VALUES (?, ?, ?, 'bcrypt')
                """, ORG_ID, ACCOUNT_ID, passwordEncoder.encode(ACCOUNT_PASSWORD));
    }

    private void insertSpaceClient() {
        jdbc.update("""
                INSERT INTO oauth2_clients (
                    id, org_id, space_id, client_id, client_name, client_type,
                    require_client_secret, client_secret_hash, token_endpoint_auth_method,
                    require_pkce, require_consent)
                VALUES (?, ?, ?, ?, ?, 'CONFIDENTIAL', TRUE, ?, 'client_secret_basic', FALSE, FALSE)
                """,
                SPACE_CLIENT_UUID, ORG_ID, SPACE_ID, SPACE_CLIENT_ID, "Baseline Space Client",
                passwordEncoder.encode(SPACE_CLIENT_SECRET));

        jdbc.update("""
                INSERT INTO oauth2_client_grant_types (id, org_id, space_id, client_id, grant_type)
                VALUES (?, ?, ?, ?, 'client_credentials')
                """, UUID.randomUUID(), ORG_ID, SPACE_ID, SPACE_CLIENT_UUID);

        jdbc.update("""
                INSERT INTO oauth2_client_scopes (id, org_id, space_id, client_id, scope)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), ORG_ID, SPACE_ID, SPACE_CLIENT_UUID, SPACE_CLIENT_SCOPE);
    }
}
