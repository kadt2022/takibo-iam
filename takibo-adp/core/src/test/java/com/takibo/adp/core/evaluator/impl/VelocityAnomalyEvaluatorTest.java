package com.takibo.adp.core.evaluator.impl;

import com.takibo.adp.api.BehaviorProfileView;
import com.takibo.adp.api.BehaviorProfileView.VelocityStats;
import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.EvaluatorResult;
import com.takibo.adp.core.evaluator.Recommendation;
import com.takibo.adp.core.model.AccessContext;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VelocityAnomalyEvaluatorTest {

    /** Ligne de base établie : 10 requêtes/min, écart type 2, 30 échantillons (le minimum). */
    private static final VelocityAnomalyEvaluator ESTABLISHED = withVelocity(new VelocityStats(10.0, 2.0, 30));
    private static final VelocityAnomalyEvaluator NO_PROFILE = new VelocityAnomalyEvaluator(id -> Optional.empty(), true);

    private static VelocityAnomalyEvaluator withVelocity(VelocityStats velocity) {
        BehaviorProfileView profile = new BehaviorProfileView("alice", Map.of(), velocity, Map.of());
        return new VelocityAnomalyEvaluator(id -> Optional.of(profile), true);
    }

    private static EvaluatorResult evaluate(VelocityAnomalyEvaluator evaluator, Integer last10s, Integer last60s) {
        return evaluator.evaluate(AccessContext.builder()
                .subjectId("alice").requestCountLast10s(last10s).requestCountLast60s(last60s).build());
    }

    @Test
    void describesItself() {
        assertThat(ESTABLISHED.getName()).isEqualTo("VelocityAnomalyEvaluator");
        assertThat(ESTABLISHED.getWeight()).isEqualTo(0.20);
        assertThat(ESTABLISHED.isEnabled()).isTrue();
        assertThat(new VelocityAnomalyEvaluator(id -> Optional.empty(), false).isEnabled()).isFalse();
    }

    @Test
    void missingCounters_isNoDataAndDoesNotReadProfile() {
        VelocityAnomalyEvaluator evaluator = new VelocityAnomalyEvaluator(id -> {
            throw new AssertionError("profile must not be read without counters");
        }, true);

        EvaluatorResult result = evaluate(evaluator, null, null);

        assertThat(result.getEvaluatorName()).isEqualTo("VelocityAnomalyEvaluator");
        assertThat(result.getRiskScore()).isEqualTo(30.0);
        assertThat(result.getConfidence()).isEqualTo(0.3);
        assertThat(result.getReason()).isEqualTo("Velocity data not available");
        assertThat(result.getRecommendation()).isEqualTo(Recommendation.ALLOW);
        assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.NO_DATA);
    }

    @Test
    void withoutProfile_normalVelocity_isBootstrapBaseline() {
        EvaluatorResult result = evaluate(NO_PROFILE, 20, 100);

        assertThat(result.getRiskScore()).isEqualTo(40.0);
        assertThat(result.getConfidence()).isEqualTo(0.35);
        assertThat(result.getReason()).isEqualTo("Velocity baseline not established");
        assertThat(result.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
        assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.NO_DATA);
    }

    @Test
    void withoutProfile_burstOrSustainedVelocityAboveLimits_isHighVelocity() {
        for (EvaluatorResult result : new EvaluatorResult[] {
                evaluate(NO_PROFILE, 21, null), evaluate(NO_PROFILE, null, 101)}) {
            assertThat(result.getRiskScore()).isEqualTo(70.0);
            assertThat(result.getConfidence()).isEqualTo(0.35);
            assertThat(result.getReason()).isEqualTo("High velocity detected (no baseline)");
            assertThat(result.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
            assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.NO_DATA);
        }
    }

    @Test
    void insufficientBaseline_fallsBackToBootstrap() {
        VelocityAnomalyEvaluator[] weakBaselines = {
                withVelocity(null),
                withVelocity(new VelocityStats(10.0, 2.0, 29)),
                withVelocity(new VelocityStats(10.0, 0.49, 500))};

        for (VelocityAnomalyEvaluator evaluator : weakBaselines) {
            assertThat(evaluate(evaluator, 1, 10).getReason()).isEqualTo("Velocity baseline not established");
            assertThat(evaluate(evaluator, 30, 10).getReason()).isEqualTo("High velocity detected (no baseline)");
        }
    }

    @Test
    void establishedBaseline_normalVelocity_isLowRisk() {
        // 1 requête en 10 s = 6 rpm (z = -2) ; 10 requêtes en 60 s = 10 rpm (z = 0).
        EvaluatorResult result = evaluate(ESTABLISHED, 1, 10);

        assertThat(result.getRiskScore()).isEqualTo(10.0);
        assertThat(result.getConfidence()).isEqualTo(0.8);
        assertThat(result.getReason()).startsWith("Velocity: current=").contains("z-score=");
        assertThat(result.getRecommendation()).isEqualTo(Recommendation.ALLOW);
        assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.OK);
    }

    @Test
    void establishedBaseline_riskGrowsWithZScore() {
        assertThat(evaluate(ESTABLISHED, null, 12).getRiskScore()).isEqualTo(10.0);   // z = 1.0
        assertThat(evaluate(ESTABLISHED, null, 13).getRiskScore()).isEqualTo(40.0);   // z = 1.5
        assertThat(evaluate(ESTABLISHED, null, 14).getRiskScore()).isEqualTo(40.0);   // z = 2.0
        assertThat(evaluate(ESTABLISHED, null, 15).getRiskScore()).isEqualTo(70.0);   // z = 2.5
        assertThat(evaluate(ESTABLISHED, null, 16).getRiskScore()).isEqualTo(70.0);   // z = 3.0
        assertThat(evaluate(ESTABLISHED, null, 17).getRiskScore()).isEqualTo(90.0);   // z = 3.5
    }

    @Test
    void establishedBaseline_burstIsScaledToRequestsPerMinute() {
        // 5 requêtes en 10 s = 30 rpm, z = 10 : le pic l'emporte sur le compteur à 60 s.
        EvaluatorResult burst = evaluate(ESTABLISHED, 5, 10);
        assertThat(burst.getRiskScore()).isEqualTo(90.0);
        assertThat(burst.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);

        EvaluatorResult moderate = evaluate(ESTABLISHED, 3, null);   // 18 rpm, z = 4
        assertThat(moderate.getRiskScore()).isEqualTo(90.0);

        EvaluatorResult elevated = evaluate(ESTABLISHED, 2, null);   // 12 rpm, z = 1
        assertThat(elevated.getRiskScore()).isEqualTo(10.0);
        assertThat(elevated.getRecommendation()).isEqualTo(Recommendation.ALLOW);
    }

    @Test
    void recommendationSwitchesToChallengeAbove60() {
        assertThat(evaluate(ESTABLISHED, null, 14).getRecommendation()).isEqualTo(Recommendation.ALLOW);     // 40
        assertThat(evaluate(ESTABLISHED, null, 15).getRecommendation()).isEqualTo(Recommendation.CHALLENGE); // 70
    }
}
