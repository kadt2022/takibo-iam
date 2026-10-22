package com.takibo.adp.core.evaluator.impl;

import com.takibo.adp.api.BehaviorProfileView;
import com.takibo.adp.api.BehaviorProfileView.FingerprintStats;
import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.EvaluatorResult;
import com.takibo.adp.core.evaluator.Recommendation;
import com.takibo.adp.core.model.AccessContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DeviceBaselineEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-10-21T14:00:00Z");

    private static DeviceBaselineEvaluator withFingerprints(Map<String, FingerprintStats> fingerprints) {
        BehaviorProfileView profile = new BehaviorProfileView("alice", fingerprints, null, Map.of());
        return new DeviceBaselineEvaluator(id -> Optional.of(profile), true);
    }

    private static DeviceBaselineEvaluator knownDevice(int seenCount, Duration firstSeenAgo, Duration lastSeenAgo) {
        return withFingerprints(Map.of("fp-1", new FingerprintStats(seenCount, NOW.minus(firstSeenAgo), NOW.minus(lastSeenAgo))));
    }

    private static EvaluatorResult evaluate(DeviceBaselineEvaluator evaluator, String fingerprint) {
        return evaluator.evaluate(AccessContext.builder().subjectId("alice").deviceFingerprint(fingerprint).timestamp(NOW).build());
    }

    /** Score attendu, recalculé à partir de la formule documentée : 0,4 récence + 0,3 fréquence + 0,3 ancienneté. */
    private static double expectedRisk(double recency, int seenCount, double age) {
        double frequency = 1.0 - Math.exp(-seenCount / 10.0);
        return 100.0 * (1.0 - (0.4 * recency + 0.3 * frequency + 0.3 * age));
    }

    @Test
    void describesItself() {
        DeviceBaselineEvaluator evaluator = withFingerprints(Map.of());
        assertThat(evaluator.getName()).isEqualTo("DeviceBaselineEvaluator");
        assertThat(evaluator.getWeight()).isEqualTo(0.25);
        assertThat(evaluator.isEnabled()).isTrue();
        assertThat(new DeviceBaselineEvaluator(id -> Optional.empty(), false).isEnabled()).isFalse();
    }

    @Test
    void missingBlankOrUnknownFingerprint_isNoDataAndDoesNotReadProfile() {
        DeviceBaselineEvaluator evaluator = new DeviceBaselineEvaluator(id -> {
            throw new AssertionError("profile must not be read without fingerprint");
        }, true);

        for (String fingerprint : new String[] {null, "  ", "UNKNOWN"}) {
            EvaluatorResult result = evaluate(evaluator, fingerprint);
            assertThat(result.getEvaluatorName()).isEqualTo("DeviceBaselineEvaluator");
            assertThat(result.getRiskScore()).isEqualTo(60.0);
            assertThat(result.getConfidence()).isEqualTo(0.5);
            assertThat(result.getReason()).isEqualTo("No device fingerprint available");
            assertThat(result.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
            assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.NO_DATA);
        }
    }

    @Test
    void unknownSubject_isNoData() {
        EvaluatorResult result = evaluate(new DeviceBaselineEvaluator(id -> Optional.empty(), true), "fp-1");

        assertThat(result.getRiskScore()).isEqualTo(50.0);
        assertThat(result.getConfidence()).isEqualTo(0.3);
        assertThat(result.getReason()).isEqualTo("No behavioral profile available");
        assertThat(result.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
        assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.NO_DATA);
    }

    @Test
    void emptyOrMissingFingerprintHistory_isNoData() {
        for (DeviceBaselineEvaluator evaluator : new DeviceBaselineEvaluator[] {withFingerprints(Map.of()), withFingerprints(null)}) {
            EvaluatorResult result = evaluate(evaluator, "fp-1");
            assertThat(result.getRiskScore()).isEqualTo(55.0);
            assertThat(result.getConfidence()).isEqualTo(0.4);
            assertThat(result.getReason()).isEqualTo("No fingerprint history");
            assertThat(result.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
            assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.NO_DATA);
        }
    }

    @Test
    void newFingerprint_isChallenged() {
        EvaluatorResult result = evaluate(knownDevice(5, Duration.ofDays(100), Duration.ofDays(1)), "fp-other");

        assertThat(result.getRiskScore()).isEqualTo(75.0);
        assertThat(result.getConfidence()).isEqualTo(0.8);
        assertThat(result.getReason()).isEqualTo("New device fingerprint detected");
        assertThat(result.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
        assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.OK);
    }

    @Test
    void longTrustedRecentDevice_isLowRisk() {
        EvaluatorResult result = evaluate(knownDevice(20, Duration.ofDays(120), Duration.ofDays(1)), "fp-1");

        assertThat(result.getRiskScore()).isCloseTo(expectedRisk(1.0, 20, 1.0), within(1e-9));
        assertThat(result.getRiskScore()).isLessThan(5.0);
        assertThat(result.getConfidence()).isEqualTo(0.85);
        assertThat(result.getReason()).isEqualTo("Known device (seen 20x, last 1d ago)");
        assertThat(result.getRecommendation()).isEqualTo(Recommendation.ALLOW);
        assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.OK);
    }

    @Test
    void recencyAndAgeBands() {
        // récence <= 30 j : 0,8 ; ancienneté >= 30 j : 0,8
        assertThat(evaluate(knownDevice(10, Duration.ofDays(45), Duration.ofDays(20)), "fp-1").getRiskScore())
                .isCloseTo(expectedRisk(0.8, 10, 0.8), within(1e-9));
        // récence <= 90 j : 0,5 ; ancienneté >= 30 j : 0,8
        assertThat(evaluate(knownDevice(10, Duration.ofDays(80), Duration.ofDays(60)), "fp-1").getRiskScore())
                .isCloseTo(expectedRisk(0.5, 10, 0.8), within(1e-9));
        // récence <= 7 j : 1,0 ; ancienneté >= 7 j : 0,5
        assertThat(evaluate(knownDevice(10, Duration.ofDays(10), Duration.ofHours(5)), "fp-1").getRiskScore())
                .isCloseTo(expectedRisk(1.0, 10, 0.5), within(1e-9));
        // ancienneté < 7 j : 0,3
        assertThat(evaluate(knownDevice(10, Duration.ofDays(2), Duration.ofMinutes(30)), "fp-1").getRiskScore())
                .isCloseTo(expectedRisk(1.0, 10, 0.3), within(1e-9));
        // limites exactes : 7 j de récence, 90 j d'ancienneté
        assertThat(evaluate(knownDevice(10, Duration.ofDays(90), Duration.ofDays(7)), "fp-1").getRiskScore())
                .isCloseTo(expectedRisk(1.0, 10, 1.0), within(1e-9));
    }

    @Test
    void reasonFormatsLastSeenInDaysHoursOrMinutes() {
        assertThat(evaluate(knownDevice(3, Duration.ofDays(10), Duration.ofHours(5)), "fp-1").getReason())
                .isEqualTo("Known device (seen 3x, last 5h ago)");
        assertThat(evaluate(knownDevice(3, Duration.ofDays(10), Duration.ofMinutes(30)), "fp-1").getReason())
                .isEqualTo("Known device (seen 3x, last 30m ago)");
    }

    @Test
    void staleRarelySeenDevice_isChallenged() {
        // récence > 90 j : 0,2 ; jamais compté : fréquence 0 ; ancienneté 1,0 -> risque 62
        EvaluatorResult result = evaluate(knownDevice(0, Duration.ofDays(200), Duration.ofDays(200)), "fp-1");

        assertThat(result.getRiskScore()).isCloseTo(62.0, within(1e-9));
        assertThat(result.getReason()).isEqualTo("Known device (seen 0x, last 200d ago)");
        assertThat(result.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
        assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.OK);
    }
}
