package com.takibo.messaging.infrastructure.health;

import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.testsupport.FakeMessageDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagingHealthIndicatorTest {

    private final FakeMessageDeliveryRepository fakeRepository = new FakeMessageDeliveryRepository();

    @Test
    void isUpWithDeadCountWhenNoDeadDeliveries() {
        Health health = new MessagingHealthIndicator(fakeRepository.repository(), 0).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(0L, health.getDetails().get("deadCount"));
    }

    @Test
    void staysUpWhenDeadCountEqualsThreshold() {
        fakeRepository.countsByStatus.put(DeliveryStatus.DEAD, 5L);

        Health health = new MessagingHealthIndicator(fakeRepository.repository(), 5).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(5L, health.getDetails().get("deadCount"));
    }

    @Test
    void warnsWhenDeadCountExceedsThreshold() {
        fakeRepository.countsByStatus.put(DeliveryStatus.DEAD, 6L);

        Health health = new MessagingHealthIndicator(fakeRepository.repository(), 5).health();

        assertEquals(new Status("WARN"), health.getStatus());
        assertEquals(6L, health.getDetails().get("deadCount"));
    }

    @Test
    void countsOnlyDeadDeliveries() {
        fakeRepository.countsByStatus.put(DeliveryStatus.FAILED, 100L);
        fakeRepository.countsByStatus.put(DeliveryStatus.PENDING, 100L);

        Health health = new MessagingHealthIndicator(fakeRepository.repository(), 0).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(0L, health.getDetails().get("deadCount"));
    }
}
