package com.takibo.outbox.spring.health;

import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxHealthIndicatorTest {

    private final OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
    private final OutboxHealthIndicator indicator = new OutboxHealthIndicator(repository, 0, 60.0);

    private Health health(long dead, double lag) {
        when(repository.countByStatus(OutboxStatus.DEAD)).thenReturn(dead);
        when(repository.runnableLagSeconds()).thenReturn(lag);
        return indicator.health();
    }

    @Test
    void noDeadMessageAndSmallLag_isUp_withDetails() {
        Health health = health(0, 12.5);

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("deadCount", 0L).containsEntry("lagSeconds", 12.5);
    }

    @Test
    void anyDeadMessage_isAWarning() {
        Health health = health(1, 0.0);

        assertThat(health.getStatus().getCode()).isEqualTo("WARN");
        assertThat(health.getDetails()).containsEntry("deadCount", 1L);
    }

    @Test
    void lagAboveThreshold_isAWarning_butLagAtThresholdIsNot() {
        assertThat(health(0, 60.0).getStatus()).isEqualTo(Status.UP);
        assertThat(health(0, 60.5).getStatus().getCode()).isEqualTo("WARN");
    }
}
