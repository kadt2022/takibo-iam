package com.takibo.authorizationserver.application;

import com.takibo.authorizationserver.domain.authorization.port.AuthorizationRetentionPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La boucle de passage (TAS-GRANTS-02B), isolee de PostgreSQL.
 * <p>
 * {@code AuthorizationRetentionIntegrationTest} prouve le comportement SQL sur une base
 * reelle. Il ne prouve pas l'enchainement des lots : combien de fois le service redemande,
 * et quand il s'arrete. C'est ce que cette classe couvre, avec un port simule dont les
 * reponses sont ecrites a l'avance.
 */
class AuthorizationRetentionServiceTest {

    private static final Duration GRACE = Duration.ofHours(1);

    @Test
    void given_full_batches_then_it_keeps_going_until_one_comes_back_incomplete() {
        FakePort port = new FakePort(10, 10, 4);
        AuthorizationRetentionService service = new AuthorizationRetentionService(port, GRACE, 10, 20);

        int deleted = service.purge();

        assertThat(deleted).isEqualTo(24);
        assertThat(port.calls)
                .as("un lot incomplet signifie qu'il n'y a plus rien a prendre")
                .hasSize(3);
    }

    @Test
    void given_an_empty_first_batch_then_it_stops_immediately() {
        FakePort port = new FakePort(0);
        AuthorizationRetentionService service = new AuthorizationRetentionService(port, GRACE, 10, 20);

        assertThat(service.purge()).isZero();
        assertThat(port.calls)
                .as("idempotence : sur une base deja purgee, un seul appel et rien d'ecrit")
                .hasSize(1);
    }

    @Test
    void given_a_backlog_larger_than_the_run_then_the_run_stays_bounded() {
        // Toujours des lots pleins : sans borne de passage, la boucle ne s'arreterait jamais.
        FakePort port = new FakePort(10, 10, 10, 10, 10, 10);
        AuthorizationRetentionService service = new AuthorizationRetentionService(port, GRACE, 10, 3);

        int deleted = service.purge();

        assertThat(deleted).isEqualTo(30);
        assertThat(port.calls)
                .as("le passage rend la main, le suivant reprendra le reliquat")
                .hasSize(3);
    }

    @Test
    void given_a_run_then_the_configured_grace_and_batch_size_reach_the_port_unchanged() {
        FakePort port = new FakePort(0);
        AuthorizationRetentionService service =
                new AuthorizationRetentionService(port, Duration.ofMinutes(90), 250, 5);

        service.purge();

        assertThat(port.calls).singleElement()
                .isEqualTo(new Call(Duration.ofMinutes(90), 250));
    }

    private record Call(Duration gracePeriod, int batchSize) {}

    /** Port simule : rend les tailles de lot prevues, puis zero. */
    private static final class FakePort implements AuthorizationRetentionPort {

        private final int[] batches;
        private final List<Call> calls = new ArrayList<>();
        private int index;

        private FakePort(int... batches) {
            this.batches = batches;
        }

        @Override
        public int purgeOneBatch(Duration gracePeriod, int batchSize) {
            calls.add(new Call(gracePeriod, batchSize));
            return index < batches.length ? batches[index++] : 0;
        }

        @Override
        public long countUnpurgeable() {
            return 0L;
        }
    }
}
