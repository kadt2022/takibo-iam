package com.takibo.outbox.spring.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "takibo.outbox")
public class OutboxProperties {

    private boolean enabled = true;

    @Valid
    private final Processor processor = new Processor();

    @Valid
    private final Backoff backoff = new Backoff();

    private boolean deadLetterLogPayload = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Processor getProcessor() {
        return processor;
    }

    public Backoff getBackoff() {
        return backoff;
    }

    public boolean isDeadLetterLogPayload() {
        return deadLetterLogPayload;
    }

    public void setDeadLetterLogPayload(boolean deadLetterLogPayload) {
        this.deadLetterLogPayload = deadLetterLogPayload;
    }

    public static class Processor {

        @Min(1)
        @Max(1000)
        private int batchSize = 50;

        private Duration fixedDelay = Duration.ofMillis(1000);

        @Min(1)
        @Max(1000)
        private int maxAttempts = 10;

        @NotBlank
        private String lockedBy = "takibo-outbox";

        private boolean schedulingEnabled = true;

        private Duration lockTimeout = Duration.ofMinutes(5);

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public Duration getFixedDelay() {
            return fixedDelay;
        }

        public void setFixedDelay(Duration fixedDelay) {
            this.fixedDelay = fixedDelay;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public String getLockedBy() {
            return lockedBy;
        }

        public void setLockedBy(String lockedBy) {
            this.lockedBy = lockedBy;
        }

        public boolean isSchedulingEnabled() {
            return schedulingEnabled;
        }

        public void setSchedulingEnabled(boolean schedulingEnabled) {
            this.schedulingEnabled = schedulingEnabled;
        }

        public Duration getLockTimeout() {
            return lockTimeout;
        }

        public void setLockTimeout(Duration lockTimeout) {
            this.lockTimeout = lockTimeout;
        }
    }

    public static class Backoff {

        private String type = "exponential";

        private Duration baseDelay = Duration.ofSeconds(1);

        private Duration maxDelay = Duration.ofSeconds(60);

        @Min(1)
        @Max(10)
        private int multiplier = 2;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Duration getBaseDelay() {
            return baseDelay;
        }

        public void setBaseDelay(Duration baseDelay) {
            this.baseDelay = baseDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
        }

        public int getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(int multiplier) {
            this.multiplier = multiplier;
        }
    }
}
