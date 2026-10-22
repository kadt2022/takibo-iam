package com.takibo.outbox.spring.health;

import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxMessageRepository repository;

    private final long deadWarnThreshold;
    private final double lagWarnSeconds;

    public OutboxHealthIndicator(OutboxMessageRepository repository, long deadWarnThreshold, double lagWarnSeconds) {
        this.repository = repository;
        this.deadWarnThreshold = deadWarnThreshold;
        this.lagWarnSeconds = lagWarnSeconds;
    }

    @Override
    public Health health() {
        long dead = repository.countByStatus(OutboxStatus.DEAD);
        double lag = repository.runnableLagSeconds();

        Health.Builder builder = Health.up()
                .withDetail("deadCount", dead)
                .withDetail("lagSeconds", lag);

        boolean warn = dead > deadWarnThreshold || lag > lagWarnSeconds;
        if (warn) {
            builder.status("WARN");
        }
        return builder.build();
    }
}
