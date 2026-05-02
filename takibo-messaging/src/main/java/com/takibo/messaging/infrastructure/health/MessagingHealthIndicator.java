package com.takibo.messaging.infrastructure.health;

import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

public class MessagingHealthIndicator implements HealthIndicator {

    private final MessageDeliveryRepository repository;
    private final long deadWarnThreshold;

    public MessagingHealthIndicator(MessageDeliveryRepository repository, long deadWarnThreshold) {
        this.repository = repository;
        this.deadWarnThreshold = deadWarnThreshold;
    }

    @Override
    public Health health() {
        long dead = repository.countByStatus(DeliveryStatus.DEAD);

        Health.Builder builder = Health.up()
                .withDetail("deadCount", dead);

        if (dead > deadWarnThreshold) {
            builder.status("WARN");
        }
        return builder.build();
    }
}
