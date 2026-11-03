package com.takibo.messaging.infrastructure.observability;

import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.testsupport.FakeMessageDeliveryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagingMetricsBinderTest {

    private final FakeMessageDeliveryRepository fakeRepository = new FakeMessageDeliveryRepository();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void exposesPendingFailedAndDeadCountsAsGauges() {
        fakeRepository.countsByStatus.put(DeliveryStatus.PENDING, 4L);
        fakeRepository.countsByStatus.put(DeliveryStatus.FAILED, 2L);
        fakeRepository.countsByStatus.put(DeliveryStatus.DEAD, 1L);
        fakeRepository.countsByStatus.put(DeliveryStatus.SENT, 99L);

        new MessagingMetricsBinder(fakeRepository.repository(), registry);

        assertEquals(4.0, gauge("takibo_messaging_pending_count"));
        assertEquals(2.0, gauge("takibo_messaging_failed_count"));
        assertEquals(1.0, gauge("takibo_messaging_dead_count"));
        assertEquals(3, registry.getMeters().size(), "aucune autre jauge n'est enregistree");
    }

    @Test
    void gaugesReadRepositoryAtEachScrape() {
        new MessagingMetricsBinder(fakeRepository.repository(), registry);
        assertEquals(0.0, gauge("takibo_messaging_dead_count"));

        fakeRepository.countsByStatus.put(DeliveryStatus.DEAD, 7L);

        assertEquals(7.0, gauge("takibo_messaging_dead_count"));
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }
}
