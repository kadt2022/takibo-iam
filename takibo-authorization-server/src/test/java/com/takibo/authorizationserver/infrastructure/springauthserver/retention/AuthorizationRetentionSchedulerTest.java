package com.takibo.authorizationserver.infrastructure.springauthserver.retention;

import com.takibo.authorizationserver.application.AuthorizationRetentionService;
import com.takibo.authorizationserver.domain.authorization.port.AuthorizationRetentionPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Le declencheur periodique de la purge (TAS-GRANTS-02B).
 * <p>
 * Le comportement teste ici est celui qui compte vraiment : un echec ne doit pas remonter.
 * L'ordonnanceur de Spring arrete definitivement une tache planifiee qui leve une exception.
 * Une base momentanement injoignable eteindrait donc la retention jusqu'au prochain
 * redemarrage, en silence, et la table recommencerait a grossir sans fin.
 */
class AuthorizationRetentionSchedulerTest {

    @Test
    void given_a_healthy_purge_then_the_tick_runs_it() {
        AtomicInteger runs = new AtomicInteger();
        AuthorizationRetentionScheduler scheduler = scheduler(() -> {
            runs.incrementAndGet();
            return 3;
        });

        scheduler.tick();

        assertThat(runs).hasValue(1);
    }

    @Test
    void given_a_failing_purge_then_the_tick_swallows_it_and_the_schedule_survives() {
        AuthorizationRetentionScheduler scheduler = scheduler(() -> {
            throw new IllegalStateException("base injoignable");
        });

        assertThatCode(scheduler::tick)
                .as("une exception remontee arreterait la tache planifiee pour de bon")
                .doesNotThrowAnyException();
    }

    @Test
    void given_a_failure_then_the_following_tick_runs_normally() {
        AtomicInteger calls = new AtomicInteger();
        AuthorizationRetentionScheduler scheduler = scheduler(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("echec passager");
            }
            return 1;
        });

        scheduler.tick();
        scheduler.tick();

        assertThat(calls)
                .as("la retention reprend d'elle-meme au prochain intervalle")
                .hasValue(2);
    }

    private static AuthorizationRetentionScheduler scheduler(Batch batch) {
        AuthorizationRetentionPort port = new AuthorizationRetentionPort() {
            @Override
            public int purgeOneBatch(Duration gracePeriod, int batchSize) {
                return batch.run();
            }

            @Override
            public long countUnpurgeable() {
                return 0L;
            }
        };
        return new AuthorizationRetentionScheduler(
                new AuthorizationRetentionService(port, Duration.ofHours(1), 10, 1));
    }

    @FunctionalInterface
    private interface Batch {
        int run();
    }
}
