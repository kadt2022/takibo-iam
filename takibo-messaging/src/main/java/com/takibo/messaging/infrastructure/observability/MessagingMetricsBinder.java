package com.takibo.messaging.infrastructure.observability;

import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

public class MessagingMetricsBinder {

    public MessagingMetricsBinder(MessageDeliveryRepository repository, MeterRegistry meterRegistry) {
        Gauge.builder("takibo_messaging_pending_count", repository, r -> r.countByStatus(DeliveryStatus.PENDING))
                .register(meterRegistry);

        Gauge.builder("takibo_messaging_failed_count", repository, r -> r.countByStatus(DeliveryStatus.FAILED))
                .register(meterRegistry);

        Gauge.builder("takibo_messaging_dead_count", repository, r -> r.countByStatus(DeliveryStatus.DEAD))
                .register(meterRegistry);
    }
}
