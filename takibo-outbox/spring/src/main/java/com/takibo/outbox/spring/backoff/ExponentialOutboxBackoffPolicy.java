package com.takibo.outbox.spring.backoff;

import com.takibo.outbox.core.port.OutboxBackoffPolicy;

import java.time.Duration;

public class ExponentialOutboxBackoffPolicy implements OutboxBackoffPolicy {

    private final Duration baseDelay;
    private final Duration maxDelay;
    private final int multiplier;

    public ExponentialOutboxBackoffPolicy(Duration baseDelay, Duration maxDelay, int multiplier) {
        if (baseDelay == null || baseDelay.isNegative() || baseDelay.isZero()) {
            throw new IllegalArgumentException("baseDelay must be positive");
        }
        if (maxDelay == null || maxDelay.isNegative() || maxDelay.isZero()) {
            throw new IllegalArgumentException("maxDelay must be positive");
        }
        if (multiplier < 1) {
            throw new IllegalArgumentException("multiplier must be >= 1");
        }
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
    }

    @Override
    public Duration nextDelay(int attempts) {
        if (attempts <= 0) {
            return baseDelay;
        }

        long millis = baseDelay.toMillis();
        for (int i = 1; i < attempts; i++) {
            millis = safeMultiply(millis, multiplier);
            if (millis >= maxDelay.toMillis()) {
                return maxDelay;
            }
        }
        return Duration.ofMillis(Math.min(millis, maxDelay.toMillis()));
    }

    private long safeMultiply(long value, int factor) {
        if (value > Long.MAX_VALUE / factor) {
            return Long.MAX_VALUE;
        }
        return value * factor;
    }
}
