package com.takibo.messaging.application.backoff;

import java.time.Duration;

public class ExponentialBackoffPolicy implements BackoffPolicy {

    private final Duration baseDelay;
    private final Duration maxDelay;
    private final int multiplier;

    public ExponentialBackoffPolicy(Duration baseDelay, Duration maxDelay, int multiplier) {
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.multiplier = Math.max(1, multiplier);
    }

    @Override
    public Duration nextDelay(int attempt) {
        int safeAttempt = Math.max(1, attempt);
        long factor = 1L;
        for (int i = 1; i < safeAttempt; i++) {
            factor = Math.min(Long.MAX_VALUE / multiplier, factor * multiplier);
        }
        Duration delay = baseDelay.multipliedBy(factor);
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }
}
