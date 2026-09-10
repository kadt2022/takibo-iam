package com.takibo.authorizationserver.infrastructure.jpa.repository;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduction du delai de grace en intervalle PostgreSQL (TAS-GRANTS-02B).
 * <p>
 * Teste ici plutot que par un test d'integration : la difference entre {@code 1500ms} et
 * {@code 1000ms} ne se prouverait sur une vraie base qu'avec des attentes reelles, donc par
 * un test lent et sensible a l'ordonnancement. La conversion, elle, est deterministe.
 * <p>
 * L'enjeu n'est pas cosmetique. Tronquer sous la seconde ferait purger <b>plus tot</b> que la
 * retention demandee, et la purge est irreversible.
 */
class JdbcAuthorizationRetentionAdapterTest {

    @Test
    void given_a_whole_number_of_seconds_then_the_interval_is_exact() {
        assertThat(JdbcAuthorizationRetentionAdapter.toInterval(Duration.ofHours(2)))
                .isEqualTo("7200 seconds 0 microseconds");
    }

    @Test
    void given_a_fractional_duration_then_the_sub_second_part_survives() {
        assertThat(JdbcAuthorizationRetentionAdapter.toInterval(Duration.ofMillis(1500)))
                .as("1500ms ne doit pas devenir 1 seconde")
                .isEqualTo("1 seconds 500000 microseconds");
    }

    @Test
    void given_a_duration_below_one_second_then_it_does_not_collapse_to_zero() {
        assertThat(JdbcAuthorizationRetentionAdapter.toInterval(Duration.ofMillis(500)))
                .as("500ms ne doit pas devenir aucune retention du tout")
                .isEqualTo("0 seconds 500000 microseconds");
    }

    @Test
    void given_zero_then_the_interval_is_zero() {
        // Purger des l'expiration est un reglage legitime.
        assertThat(JdbcAuthorizationRetentionAdapter.toInterval(Duration.ZERO))
                .isEqualTo("0 seconds 0 microseconds");
    }

    @Test
    void given_a_precision_finer_than_postgresql_then_it_is_rounded_to_the_microsecond() {
        // La microseconde est la precision reelle du type interval : afficher davantage
        // laisserait croire a une exactitude que la base ne conserve pas.
        assertThat(JdbcAuthorizationRetentionAdapter.toInterval(Duration.ofNanos(1_999)))
                .isEqualTo("0 seconds 1 microseconds");
    }
}
