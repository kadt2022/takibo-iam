package com.takibo.authorizationserver.infrastructure.jpa.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    // ─────────────────────────────────────────────────────────────
    // Gardes d'entree
    // ─────────────────────────────────────────────────────────────
    // Refusees avant d'atteindre la base : une taille de lot nulle produirait une purge
    // silencieusement inerte, et un delai negatif purgerait dans le futur. Les deux sont des
    // erreurs de configuration, et une erreur de configuration doit se voir tout de suite.

    @Test
    void given_a_non_positive_batch_size_then_the_purge_refuses_to_run() {
        JdbcAuthorizationRetentionAdapter adapter = new JdbcAuthorizationRetentionAdapter(null);

        assertThatThrownBy(() -> adapter.purgeOneBatch(Duration.ofHours(1), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RETENTION_BATCH_SIZE_MUST_BE_POSITIVE");

        assertThatThrownBy(() -> adapter.purgeOneBatch(Duration.ofHours(1), -5))
                .hasMessageContaining("RETENTION_BATCH_SIZE_MUST_BE_POSITIVE");
    }

    @Test
    void given_a_negative_grace_period_then_the_purge_refuses_to_run() {
        JdbcAuthorizationRetentionAdapter adapter = new JdbcAuthorizationRetentionAdapter(null);

        assertThatThrownBy(() -> adapter.purgeOneBatch(Duration.ofSeconds(-1), 10))
                .hasMessageContaining("RETENTION_GRACE_PERIOD_MUST_NOT_BE_NEGATIVE");

        assertThatThrownBy(() -> adapter.purgeOneBatch(null, 10))
                .hasMessageContaining("RETENTION_GRACE_PERIOD_MUST_NOT_BE_NEGATIVE");
    }

    // ─────────────────────────────────────────────────────────────
    // Ce qui part vraiment vers la base
    // ─────────────────────────────────────────────────────────────
    // AuthorizationRetentionIntegrationTest prouve le resultat sur PostgreSQL reel. Il ne
    // montre pas ce que la requete contient. Deux proprietes de cette requete sont des
    // decisions de conception qu'une refonte pourrait defaire sans qu'aucun test
    // d'integration ne le remarque : l'horloge employee, et le fait de ne jamais materialiser
    // plus d'un lot.

    @Test
    void given_a_purge_then_the_query_uses_the_database_clock_and_skips_locked_rows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // Le tableau varargs est apparie d'un bloc : deux any() separes ne se lient pas a la
        // bonne surcharge de update(...), et la stub resterait silencieusement inactive.
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(7);
        JdbcAuthorizationRetentionAdapter adapter = new JdbcAuthorizationRetentionAdapter(jdbc);

        int deleted = adapter.purgeOneBatch(Duration.ofMinutes(90), 250);

        assertThat(deleted).isEqualTo(7);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> grace = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> limit = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(sql.capture(), grace.capture(), limit.capture());

        assertThat(sql.getValue())
                .as("l'horloge doit etre celle de PostgreSQL, jamais celle d'une JVM")
                .contains("CURRENT_TIMESTAMP")
                .as("sans SKIP LOCKED, deux instances se bloqueraient au lieu de se repartir")
                .contains("FOR UPDATE SKIP LOCKED")
                .as("le lot est borne dans la requete elle-meme")
                .contains("LIMIT ?");
        assertThat(grace.getValue()).isEqualTo("5400 seconds 0 microseconds");
        assertThat(limit.getValue()).isEqualTo(250);
    }

    @Test
    void given_no_row_returned_by_the_count_then_it_reads_as_zero() {
        // queryForObject peut rendre null : le traduire en zero evite un
        // NullPointerException dans une tache de fond qui, en remontant, arreterait
        // definitivement l'ordonnancement.
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

        assertThat(new JdbcAuthorizationRetentionAdapter(jdbc).countUnpurgeable()).isZero();
    }

    @Test
    void given_anomalies_then_their_number_is_reported_as_is() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(4L);

        assertThat(new JdbcAuthorizationRetentionAdapter(jdbc).countUnpurgeable()).isEqualTo(4L);
    }
}
