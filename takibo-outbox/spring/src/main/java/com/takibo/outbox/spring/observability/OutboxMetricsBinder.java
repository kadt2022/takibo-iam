package com.takibo.outbox.spring.observability;

import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

public class OutboxMetricsBinder {

    public OutboxMetricsBinder(OutboxMessageRepository repository, MeterRegistry meterRegistry) {
        Gauge.builder("takibo_outbox_pending_count", repository, r -> r.countByStatus(OutboxStatus.PENDING))
                .register(meterRegistry);

        Gauge.builder("takibo_outbox_failed_count", repository, r -> r.countByStatus(OutboxStatus.FAILED))
                .register(meterRegistry);

        Gauge.builder("takibo_outbox_dead_count", repository, r -> r.countByStatus(OutboxStatus.DEAD))
                .register(meterRegistry);

        Gauge.builder("takibo_outbox_lag_seconds", repository, OutboxMessageRepository::runnableLagSeconds)
                .register(meterRegistry);
    }
}
