package com.takibo.outbox.spring.scheduling;

import com.takibo.outbox.spring.processor.OutboxProcessor;
import org.springframework.scheduling.annotation.Scheduled;

public class OutboxScheduler {

    private final OutboxProcessor processor;

    public OutboxScheduler(OutboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(
            fixedDelayString = "${takibo.outbox.processor.fixed-delay:2s}",
            initialDelayString = "${takibo.outbox.processor.initial-delay:2s}"
    )
    public void tick() {
        processor.processOnce();
    }
}
