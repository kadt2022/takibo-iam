package com.takibo.outbox.spring.backoff;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExponentialOutboxBackoffPolicyTest {

    @Test
    void capsAtMaxDelay() {
        ExponentialOutboxBackoffPolicy policy = new ExponentialOutboxBackoffPolicy(Duration.ofSeconds(1), Duration.ofSeconds(10), 2);
        assertEquals(Duration.ofSeconds(1), policy.nextDelay(1));
        assertEquals(Duration.ofSeconds(2), policy.nextDelay(2));
        assertEquals(Duration.ofSeconds(4), policy.nextDelay(3));
        assertEquals(Duration.ofSeconds(8), policy.nextDelay(4));
        assertEquals(Duration.ofSeconds(10), policy.nextDelay(5));
        assertEquals(Duration.ofSeconds(10), policy.nextDelay(10));
    }
}
