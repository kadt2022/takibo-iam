package com.takibo.iamboot.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DatabaseMigrationTest {

    @Test
    void given_tms_code_normalization_migration_when_non_ascii_guard_is_checked_then_it_runs_before_sql_normalization() throws Exception {
        String migration = readMigration("db/migration/V202606240001__tms__normalize_org_space_codes.sql");

        int nonAsciiDiagnostic = migration.indexOf("DIAGNOSTIC: non-ASCII legacy codes");
        int orgAsciiGuard = migration.indexOf("WHERE  code !~ '^[A-Za-z0-9 _.-]+$'");
        int firstCollisionDiagnostic = migration.indexOf("would collide after normalization");
        int firstUpdate = migration.indexOf("UPDATE organizations");

        assertThat(nonAsciiDiagnostic).isGreaterThanOrEqualTo(0);
        assertThat(orgAsciiGuard).isGreaterThan(nonAsciiDiagnostic);
        assertThat(firstCollisionDiagnostic).isGreaterThan(orgAsciiGuard);
        assertThat(firstUpdate).isGreaterThan(firstCollisionDiagnostic);
        assertThat(migration).contains("MIGRATION BLOCKED: % organization code(s) contain non-ASCII characters");
        assertThat(migration).contains("MIGRATION BLOCKED: % space code(s) contain non-ASCII characters");
        assertThat(migration).contains("THEN 'space-' || lpad(");
    }

    @Test
    void given_configured_database_check_environment_when_flyway_migrates_then_all_migrations_apply() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("TAKIBO_DATABASE_CHECK")),
                "Only runs in the database-check CI job");

        String jdbcUrl = requiredEnv("SPRING_DATASOURCE_URL");
        String username = requiredEnv("SPRING_DATASOURCE_USERNAME");
        String password = requiredEnv("SPRING_DATASOURCE_PASSWORD");

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(flyway.info().pending()).isEmpty();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select count(*) from flyway_schema_history")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isGreaterThan(0);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        assertThat(value)
                .as(name + " must be set by the database-check job")
                .isNotBlank();
        return value;
    }

    private static String readMigration(String resourcePath) throws Exception {
        try (var stream = DatabaseMigrationTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(stream).as(resourcePath + " must exist").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
