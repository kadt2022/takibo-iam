package com.takibo.adp.core.evaluator.impl;

import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.EvaluatorResult;
import com.takibo.adp.core.evaluator.Recommendation;
import com.takibo.adp.core.model.AccessContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TimeRiskEvaluatorTest {

    private final TimeRiskEvaluator evaluator = new TimeRiskEvaluator(true);

    private static EvaluatorResult evaluateAt(String instant, ZoneId zone) {
        AccessContext context = AccessContext.builder()
                .subjectId("alice")
                .timestamp(instant == null ? null : Instant.parse(instant))
                .userTimezone(zone)
                .build();
        return new TimeRiskEvaluator(true).evaluate(context);
    }

    @Test
    void describesItself() {
        assertThat(evaluator.getName()).isEqualTo("TimeRiskEvaluator");
        assertThat(evaluator.getWeight()).isEqualTo(0.10);
        assertThat(evaluator.isEnabled()).isTrue();
        assertThat(new TimeRiskEvaluator(false).isEnabled()).isFalse();
    }

    @Test
    void missingTimestamp_isNoData() {
        EvaluatorResult result = evaluateAt(null, ZoneOffset.UTC);

        assertThat(result.getEvaluatorName()).isEqualTo("TimeRiskEvaluator");
        assertThat(result.getRiskScore()).isEqualTo(50.0);
        assertThat(result.getConfidence()).isEqualTo(0.3);
        assertThat(result.getReason()).isEqualTo("No timestamp available");
        assertThat(result.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
        assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.NO_DATA);
    }

    @Test
    void weekdayDuringBusinessHours_isLowRisk() {
        // 2026-10-21 est un mercredi.
        EvaluatorResult result = evaluateAt("2026-10-21T10:00:00Z", ZoneOffset.UTC);

        assertThat(result.getRiskScore()).isEqualTo(15.0);
        assertThat(result.getConfidence()).isEqualTo(0.8);
        assertThat(result.getReason()).isEqualTo("Access during typical business hours");
        assertThat(result.getRecommendation()).isEqualTo(Recommendation.ALLOW);
        assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.OK);
    }

    @Test
    void weekend_isOutsideBusinessHours() {
        EvaluatorResult result = evaluateAt("2026-10-24T10:00:00Z", ZoneOffset.UTC);

        assertThat(result.getRiskScore()).isEqualTo(55.0);
        assertThat(result.getConfidence()).isEqualTo(0.7);
        assertThat(result.getReason()).isEqualTo("Access outside typical business hours");
        assertThat(result.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
        assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.OK);
    }

    @Test
    void businessHourBoundaries_areInclusiveFrom8To18() {
        assertThat(evaluateAt("2026-10-21T07:59:59Z", ZoneOffset.UTC).getRiskScore()).isEqualTo(55.0);
        assertThat(evaluateAt("2026-10-21T08:00:00Z", ZoneOffset.UTC).getRiskScore()).isEqualTo(15.0);
        assertThat(evaluateAt("2026-10-21T18:59:59Z", ZoneOffset.UTC).getRiskScore()).isEqualTo(15.0);
        assertThat(evaluateAt("2026-10-21T19:00:00Z", ZoneOffset.UTC).getRiskScore()).isEqualTo(55.0);
        // Vendredi soir et lundi matin : limites des jours ouvrés.
        assertThat(evaluateAt("2026-10-23T12:00:00Z", ZoneOffset.UTC).getRiskScore()).isEqualTo(15.0);
        assertThat(evaluateAt("2026-10-25T12:00:00Z", ZoneOffset.UTC).getRiskScore()).isEqualTo(55.0);
        assertThat(evaluateAt("2026-10-26T12:00:00Z", ZoneOffset.UTC).getRiskScore()).isEqualTo(15.0);
    }

    @Test
    void businessHoursAreComputedInTheUserTimezone() {
        // 06:00 UTC = 08:00 à Paris (heure d'été jusqu'au 25 octobre 2026).
        assertThat(evaluateAt("2026-10-21T06:00:00Z", ZoneOffset.UTC).getRiskScore()).isEqualTo(55.0);
        assertThat(evaluateAt("2026-10-21T06:00:00Z", ZoneId.of("Europe/Paris")).getRiskScore()).isEqualTo(15.0);
    }
}
