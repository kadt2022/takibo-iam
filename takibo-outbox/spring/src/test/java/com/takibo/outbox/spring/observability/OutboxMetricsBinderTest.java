package com.takibo.outbox.spring.observability;

import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsBinderTest {

    @Test
    void gaugesReadTheCurrentOutboxStateOnEachScrape() {
        OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        new OutboxMetricsBinder(repository, meters);

        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(4L);
        when(repository.countByStatus(OutboxStatus.FAILED)).thenReturn(2L);
        when(repository.countByStatus(OutboxStatus.DEAD)).thenReturn(1L);
        when(repository.runnableLagSeconds()).thenReturn(7.5);

        assertThat(meters.get("takibo_outbox_pending_count").gauge().value()).isEqualTo(4.0);
        assertThat(meters.get("takibo_outbox_failed_count").gauge().value()).isEqualTo(2.0);
        assertThat(meters.get("takibo_outbox_dead_count").gauge().value()).isEqualTo(1.0);
        assertThat(meters.get("takibo_outbox_lag_seconds").gauge().value()).isEqualTo(7.5);

        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(0L);
        assertThat(meters.get("takibo_outbox_pending_count").gauge().value()).isZero();
    }
}
