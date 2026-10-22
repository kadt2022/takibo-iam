package com.takibo.adp.core.engine;

import com.takibo.adp.api.Decision;
import com.takibo.adp.api.DecisionFactor;
import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.api.DecisionResponse;
import com.takibo.adp.api.DecisionStatus;
import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.api.Thresholds;
import com.takibo.adp.core.evaluator.ContextEvaluator;
import com.takibo.adp.core.evaluator.Recommendation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Remplace l'ancien DecisionEngineTest, entièrement commenté. Les évaluateurs sont factices
 * (résultat fixe) ; l'exécuteur, l'agrégation et la règle de décision sont les vrais.
 */
class DecisionEngineTest {

    private static final Thresholds THRESHOLDS = new Thresholds(75.0, 50.0, "test policy");
    private static final Instant NOW = Instant.parse("2026-10-21T14:00:00Z");

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    private DecisionResponse evaluate(List<ContextEvaluator> evaluators) {
        DecisionEngine engine = new DecisionEngine(
                evaluators, request -> THRESHOLDS, new AdpExecutor(executorService), new AggregationStrategy());
        return engine.evaluate(request());
    }

    private static DecisionRequest request() {
        return new DecisionRequest("alice", "org", "space", Set.of("USER"), Set.of(), "/api/data", "GET",
                NOW, "10.0.0.1", "fp", "agent", null, null, null, null, null, null, 1, 1,
                1000, "1.0", Map.of());
    }

    @Test
    void lowRiskWithConfidentEvaluators_isAllowed() {
        DecisionResponse response = evaluate(List.of(
                StubEvaluator.ok("a", 0.5, 10.0, 0.9, Recommendation.ALLOW),
                StubEvaluator.ok("b", 0.5, 20.0, 0.9, Recommendation.ALLOW)));

        assertThat(response.decision()).isEqualTo(Decision.ALLOW);
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.riskScore()).isEqualTo(15.0);
        assertThat(response.status()).isEqualTo(DecisionStatus.OK);
        assertThat(response.thresholds()).isEqualTo(THRESHOLDS);
        assertThat(response.timestamp()).isEqualTo(NOW);
        assertThat(response.decisionId()).isNotBlank();
    }

    @Test
    void scoreAtDenyThresholdWithHighConfidence_isDenied() {
        DecisionResponse response = evaluate(List.of(
                StubEvaluator.ok("a", 0.5, 75.0, 0.9, Recommendation.DENY),
                StubEvaluator.ok("b", 0.5, 75.0, 0.9, Recommendation.DENY)));

        assertThat(response.riskScore()).isEqualTo(75.0);
        assertThat(response.decision()).isEqualTo(Decision.DENY);
        assertThat(response.isDenied()).isTrue();
    }

    @Test
    void highScoreWithoutEnoughConfidenceToDeny_isChallenged() {
        // Score au-dessus du seuil de refus, mais confiance effective 0,6 < 0,7 : pas de DENY.
        DecisionResponse response = evaluate(List.of(
                StubEvaluator.ok("a", 0.5, 90.0, 0.6, Recommendation.DENY),
                StubEvaluator.ok("b", 0.5, 90.0, 0.6, Recommendation.DENY)));

        assertThat(response.confidence()).isEqualTo(0.6);
        assertThat(response.decision()).isEqualTo(Decision.CHALLENGE);
        assertThat(response.requiresChallenge()).isTrue();
    }

    @Test
    void scoreBetweenChallengeAndDenyThresholds_isChallenged() {
        DecisionResponse response = evaluate(List.of(
                StubEvaluator.ok("a", 1.0, 50.0, 0.9, Recommendation.CHALLENGE)));

        assertThat(response.riskScore()).isEqualTo(50.0);
        assertThat(response.decision()).isEqualTo(Decision.CHALLENGE);
    }

    @Test
    void scoreJustBelowChallengeThreshold_isAllowed() {
        DecisionResponse response = evaluate(List.of(
                StubEvaluator.ok("a", 1.0, 49.9, 0.9, Recommendation.ALLOW)));

        assertThat(response.decision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    void lowConfidence_forcesChallengeEvenForALowScore() {
        // Confiance effective 0,1 < 0,2 : CHALLENGE quel que soit le score.
        DecisionResponse response = evaluate(List.of(
                StubEvaluator.ok("a", 1.0, 5.0, 0.1, Recommendation.ALLOW)));

        assertThat(response.decision()).isEqualTo(Decision.CHALLENGE);
    }

    @Test
    void noEnabledEvaluator_isAnErrorAndAChallenge() {
        DecisionResponse response = evaluate(List.of(StubEvaluator.disabled("off")));

        assertThat(response.status()).isEqualTo(DecisionStatus.ERROR);
        assertThat(response.decision()).isEqualTo(Decision.CHALLENGE);
        assertThat(response.riskScore()).isEqualTo(50.0);
        assertThat(response.confidence()).isZero();
        assertThat(response.topFactors()).isEmpty();
    }

    @Test
    void someEvaluatorsWithoutData_givePartialStatus() {
        DecisionResponse response = evaluate(List.of(
                StubEvaluator.ok("a", 0.5, 10.0, 0.9, Recommendation.ALLOW),
                StubEvaluator.withStatus("b", 0.5, 40.0, 0.3, EvaluatorStatus.NO_DATA)));

        assertThat(response.status()).isEqualTo(DecisionStatus.PARTIAL);
        assertThat(response.explanation()).contains("Warning: 1 evaluators incomplete.");
    }

    @Test
    void onlyTimeouts_giveTimeoutStatus() {
        DecisionResponse response = evaluate(List.of(
                StubEvaluator.withStatus("a", 0.5, 50.0, 0.0, EvaluatorStatus.TIMEOUT),
                StubEvaluator.withStatus("b", 0.5, 50.0, 0.0, EvaluatorStatus.TIMEOUT)));

        assertThat(response.status()).isEqualTo(DecisionStatus.TIMEOUT);
        assertThat(response.decision()).isEqualTo(Decision.CHALLENGE);
    }

    @Test
    void noEvaluatorOk_givesErrorStatus() {
        DecisionResponse response = evaluate(List.of(
                StubEvaluator.withStatus("a", 0.5, 50.0, 0.0, EvaluatorStatus.ERROR),
                StubEvaluator.withStatus("b", 0.5, 50.0, 0.0, EvaluatorStatus.TIMEOUT)));

        assertThat(response.status()).isEqualTo(DecisionStatus.ERROR);
    }

    @Test
    void explanation_namesThresholdsAndTheThreeMainOkContributorsAboveFifteen() {
        DecisionResponse response = evaluate(List.of(
                StubEvaluator.ok("low", 0.2, 10.0, 0.9, Recommendation.ALLOW),
                StubEvaluator.ok("mid", 0.2, 40.0, 0.9, Recommendation.ALLOW),
                StubEvaluator.ok("high", 0.2, 60.0, 0.9, Recommendation.CHALLENGE),
                StubEvaluator.ok("top", 0.2, 70.0, 0.9, Recommendation.CHALLENGE),
                StubEvaluator.ok("next", 0.2, 30.0, 0.9, Recommendation.ALLOW)));

        assertThat(response.explanation())
                .startsWith("Risk score: 42.0/100 (confidence: 0.90).")
                .contains("Thresholds: deny=75, challenge=50 (test policy).")
                .contains("Main factors: top (70): top reason; high (60): high reason; mid (40): mid reason;")
                .doesNotContain("next (30)")
                .doesNotContain("low (10)")
                .doesNotContain("Warning");
    }

    @Test
    void topFactors_areSortedByRiskAndLimitedToFive() {
        DecisionResponse response = evaluate(List.of(
                StubEvaluator.ok("e1", 0.1, 10.0, 0.9, Recommendation.ALLOW),
                StubEvaluator.ok("e2", 0.1, 60.0, 0.9, Recommendation.ALLOW),
                StubEvaluator.ok("e3", 0.1, 20.0, 0.9, Recommendation.ALLOW),
                StubEvaluator.ok("e4", 0.1, 50.0, 0.9, Recommendation.ALLOW),
                StubEvaluator.ok("e5", 0.1, 30.0, 0.9, Recommendation.ALLOW),
                StubEvaluator.withStatus("e6", 0.1, 40.0, 0.2, EvaluatorStatus.NO_DATA)));

        assertThat(response.topFactors()).extracting(DecisionFactor::evaluatorName)
                .containsExactly("e2", "e4", "e6", "e5", "e3");
        DecisionFactor first = response.topFactors().get(0);
        assertThat(first.riskScore()).isEqualTo(60.0);
        assertThat(first.confidence()).isEqualTo(0.9);
        assertThat(first.reason()).isEqualTo("e2 reason");
        assertThat(first.status()).isEqualTo(EvaluatorStatus.OK);
    }
}
