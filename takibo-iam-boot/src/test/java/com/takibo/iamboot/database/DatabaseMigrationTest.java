package com.takibo.iamboot.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DatabaseMigrationTest {

    @Test
    void flywayMigrationsApplyOnConfiguredDatabase() throws Exception {
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
}
