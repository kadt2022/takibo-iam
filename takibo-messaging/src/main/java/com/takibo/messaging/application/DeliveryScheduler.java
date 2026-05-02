package com.takibo.messaging.application;

import org.springframework.scheduling.annotation.Scheduled;

public class DeliveryScheduler {

    private final DeliveryProcessor processor;

    public DeliveryScheduler(DeliveryProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${takibo.messaging.processor.fixed-delay:2s}")
    public void tick() {
        if (!processor.hasRunnable()) {
            return;
        }
        processor.processBatch();
    }
}
