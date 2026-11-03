package com.takibo.messaging.application.backoff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExponentialBackoffPolicyTest {

    private final ExponentialBackoffPolicy policy =
            new ExponentialBackoffPolicy(Duration.ofSeconds(5), Duration.ofMinutes(10), 2);

    @ParameterizedTest(name = "tentative {0} -> {1}s")
    @CsvSource({"1,5", "2,10", "3,20", "4,40", "5,80", "7,320"})
    void growsExponentiallyFromBaseDelay(int attempt, long expectedSeconds) {
        assertEquals(Duration.ofSeconds(expectedSeconds), policy.nextDelay(attempt));
    }

    @Test
    void capsDelayAtMaxDelay() {
        assertEquals(Duration.ofMinutes(10), policy.nextDelay(8));
        assertEquals(Duration.ofMinutes(10), policy.nextDelay(30));
    }

    @Test
    void treatsZeroOrNegativeAttemptAsFirstAttempt() {
        assertEquals(Duration.ofSeconds(5), policy.nextDelay(0));
        assertEquals(Duration.ofSeconds(5), policy.nextDelay(-3));
    }

    @Test
    void multiplierBelowOneIsTreatedAsConstantBackoff() {
        ExponentialBackoffPolicy constant = new ExponentialBackoffPolicy(Duration.ofSeconds(7), Duration.ofMinutes(1), 0);

        assertEquals(Duration.ofSeconds(7), constant.nextDelay(1));
        assertEquals(Duration.ofSeconds(7), constant.nextDelay(10));
    }

    @Test
    void supportsMultipliersOtherThanTwo() {
        ExponentialBackoffPolicy triple = new ExponentialBackoffPolicy(Duration.ofSeconds(1), Duration.ofHours(1), 3);

        assertEquals(Duration.ofSeconds(9), triple.nextDelay(3));
    }

    @Test
    void doesNotOverflowFactorForLargeAttemptWithNanosecondBase() {
        ExponentialBackoffPolicy tiny = new ExponentialBackoffPolicy(Duration.ofNanos(1), Duration.ofDays(1), 2);

        assertEquals(Duration.ofDays(1), tiny.nextDelay(500));
    }
}
