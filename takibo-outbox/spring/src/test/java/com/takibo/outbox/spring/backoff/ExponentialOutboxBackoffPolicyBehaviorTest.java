package com.takibo.outbox.spring.backoff;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExponentialOutboxBackoffPolicyBehaviorTest {

    @Test
    void firstAttemptAndInvalidAttemptCounts_useTheBaseDelay() {
        ExponentialOutboxBackoffPolicy policy = new ExponentialOutboxBackoffPolicy(Duration.ofMillis(500), Duration.ofSeconds(10), 2);

        assertThat(policy.nextDelay(0)).isEqualTo(Duration.ofMillis(500));
        assertThat(policy.nextDelay(-3)).isEqualTo(Duration.ofMillis(500));
        assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void multiplierOne_meansAConstantDelay() {
        ExponentialOutboxBackoffPolicy policy = new ExponentialOutboxBackoffPolicy(Duration.ofSeconds(3), Duration.ofSeconds(10), 1);

        assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofSeconds(3));
        assertThat(policy.nextDelay(50)).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void baseDelayAboveMax_isCappedAtMax() {
        ExponentialOutboxBackoffPolicy policy = new ExponentialOutboxBackoffPolicy(Duration.ofSeconds(30), Duration.ofSeconds(10), 3);

        assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void veryLargeAttemptCounts_doNotOverflow() {
        ExponentialOutboxBackoffPolicy policy = new ExponentialOutboxBackoffPolicy(
                Duration.ofMillis(Long.MAX_VALUE / 4), Duration.ofMillis(Long.MAX_VALUE), 10);

        assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofMillis(Long.MAX_VALUE / 4));
        assertThat(policy.nextDelay(1_000)).isEqualTo(Duration.ofMillis(Long.MAX_VALUE));
    }

    @Test
    void invalidConfiguration_isRejected() {
        Duration ok = Duration.ofSeconds(1);
        assertThatThrownBy(() -> new ExponentialOutboxBackoffPolicy(null, ok, 2)).hasMessage("baseDelay must be positive");
        assertThatThrownBy(() -> new ExponentialOutboxBackoffPolicy(Duration.ZERO, ok, 2)).hasMessage("baseDelay must be positive");
        assertThatThrownBy(() -> new ExponentialOutboxBackoffPolicy(Duration.ofSeconds(-1), ok, 2)).hasMessage("baseDelay must be positive");
        assertThatThrownBy(() -> new ExponentialOutboxBackoffPolicy(ok, null, 2)).hasMessage("maxDelay must be positive");
        assertThatThrownBy(() -> new ExponentialOutboxBackoffPolicy(ok, Duration.ZERO, 2)).hasMessage("maxDelay must be positive");
        assertThatThrownBy(() -> new ExponentialOutboxBackoffPolicy(ok, Duration.ofSeconds(-1), 2)).hasMessage("maxDelay must be positive");
        assertThatThrownBy(() -> new ExponentialOutboxBackoffPolicy(ok, ok, 0)).hasMessage("multiplier must be >= 1");
    }
}
