package com.takibo.outbox.spring.scheduling;

import com.takibo.outbox.spring.config.OutboxProperties;
import com.takibo.outbox.spring.processor.OutboxProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

@ConditionalOnProperty(prefix = "takibo.outbox.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

    private final OutboxProcessor processor;

    public OutboxScheduler(OutboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(
            fixedDelayString = "${takibo.outbox.scheduler.fixed-delay:2s}",
            initialDelayString = "${takibo.outbox.scheduler.initial-delay:2s}"
    )
    public void tick() {
        processor.processOnce();
    }
}
