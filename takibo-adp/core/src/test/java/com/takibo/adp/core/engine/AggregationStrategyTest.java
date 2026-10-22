package com.takibo.adp.core.engine;

import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.ContextEvaluator;
import com.takibo.adp.core.evaluator.EvaluatorResult;
import com.takibo.adp.core.evaluator.Recommendation;
import com.takibo.adp.core.model.RiskAssessment;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.takibo.adp.core.engine.StubEvaluator.result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AggregationStrategyTest {

    private final AggregationStrategy strategy = new AggregationStrategy();

    private static final List<ContextEvaluator> WEIGHTS = List.of(
            StubEvaluator.ok("heavy", 0.75, 0, 0, Recommendation.ALLOW),
            StubEvaluator.ok("light", 0.25, 0, 0, Recommendation.ALLOW));

    @Test
    void noResult_isMaximalUncertaintyWithANeutralScore() {
        for (List<EvaluatorResult> results : Arrays.asList(null, List.<EvaluatorResult>of())) {
            RiskAssessment assessment = strategy.aggregate(results, WEIGHTS);

            assertThat(assessment.getAggregatedScore()).isEqualTo(50.0);
            assertThat(assessment.getConfidence()).isZero();
            assertThat(assessment.getUncertainty()).isEqualTo(1.0);
            assertThat(assessment.getMajorityRecommendation()).isEqualTo(Recommendation.CHALLENGE);
            assertThat(assessment.getEvaluatorCount()).isZero();
            assertThat(assessment.getHealthRatio()).isZero();
        }
    }

    @Test
    void score_isTheAverageWeightedByEvaluatorWeightAndConfidence() {
        RiskAssessment assessment = strategy.aggregate(List.of(
                result("heavy", 80.0, 1.0, Recommendation.DENY, EvaluatorStatus.OK),
                result("light", 20.0, 1.0, Recommendation.ALLOW, EvaluatorStatus.OK)), WEIGHTS);

        // (80 x 0,75 + 20 x 0,25) / (0,75 + 0,25) = 65
        assertThat(assessment.getAggregatedScore()).isCloseTo(65.0, within(1e-9));
        assertThat(assessment.getConfidence()).isEqualTo(1.0);
        assertThat(assessment.getUncertainty()).isZero();
        assertThat(assessment.getHealthRatio()).isEqualTo(1.0);
        assertThat(assessment.getEvaluatorCount()).isEqualTo(2);
    }

    @Test
    void anEvaluatorWithoutConfidence_doesNotWeighOnTheScore() {
        RiskAssessment assessment = strategy.aggregate(List.of(
                result("heavy", 90.0, 0.0, Recommendation.DENY, EvaluatorStatus.OK),
                result("light", 20.0, 0.5, Recommendation.ALLOW, EvaluatorStatus.OK)), WEIGHTS);

        assertThat(assessment.getAggregatedScore()).isCloseTo(20.0, within(1e-9));
    }

    @Test
    void unknownEvaluatorName_getsTheDefaultWeight() {
        RiskAssessment assessment = strategy.aggregate(List.of(
                result("heavy", 100.0, 1.0, Recommendation.DENY, EvaluatorStatus.OK),
                result("unknown", 0.0, 1.0, Recommendation.ALLOW, EvaluatorStatus.OK)), WEIGHTS);

        // poids par défaut 0,1 : 100 x 0,75 / (0,75 + 0,1)
        assertThat(assessment.getAggregatedScore()).isCloseTo(75.0 / 0.85, within(1e-9));
    }

    @Test
    void confidence_isTheAverageConfidenceDiscountedByTheShareOfHealthyEvaluators() {
        RiskAssessment assessment = strategy.aggregate(List.of(
                result("heavy", 40.0, 0.8, Recommendation.ALLOW, EvaluatorStatus.OK),
                result("light", 40.0, 0.4, Recommendation.ALLOW, EvaluatorStatus.NO_DATA)), WEIGHTS);

        assertThat(assessment.getHealthRatio()).isEqualTo(0.5);
        assertThat(assessment.getConfidence()).isCloseTo(0.6 * 0.5, within(1e-9));
        assertThat(assessment.getUncertainty()).isCloseTo(0.7, within(1e-9));
    }

    @Test
    void invalidConfidences_areClampedBetweenZeroAndOne() {
        RiskAssessment assessment = strategy.aggregate(List.of(
                result("heavy", 80.0, Double.NaN, Recommendation.DENY, EvaluatorStatus.OK),
                result("light", 20.0, 7.0, Recommendation.ALLOW, EvaluatorStatus.OK)), WEIGHTS);

        // NaN compte pour 0 (le score de « heavy » n'intervient pas), 7 compte pour 1.
        assertThat(assessment.getAggregatedScore()).isCloseTo(20.0, within(1e-9));
        assertThat(assessment.getConfidence()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void allConfidencesNull_fallBackToTheNeutralScore() {
        RiskAssessment assessment = strategy.aggregate(List.of(
                result("heavy", 90.0, 0.0, Recommendation.DENY, EvaluatorStatus.TIMEOUT)), WEIGHTS);

        assertThat(assessment.getAggregatedScore()).isEqualTo(50.0);
        assertThat(assessment.getHealthRatio()).isZero();
    }

    @Test
    void nullResultsAreSkipped_butStillCountInTheAverages() {
        RiskAssessment assessment = strategy.aggregate(Arrays.asList(
                result("heavy", 60.0, 1.0, Recommendation.ALLOW, EvaluatorStatus.OK), null), WEIGHTS);

        assertThat(assessment.getAggregatedScore()).isCloseTo(60.0, within(1e-9));
        assertThat(assessment.getEvaluatorCount()).isEqualTo(2);
        assertThat(assessment.getHealthRatio()).isEqualTo(0.5);
    }

    @Test
    void evaluatorsWithoutNameAreIgnoredForWeights_andNullEvaluatorListIsTolerated() {
        List<ContextEvaluator> evaluators = Arrays.asList(
                null, StubEvaluator.ok(" ", 0.9, 0, 0, Recommendation.ALLOW));

        RiskAssessment withBlankNames = strategy.aggregate(List.of(
                result(" ", 30.0, 1.0, Recommendation.ALLOW, EvaluatorStatus.OK)), evaluators);
        RiskAssessment withoutEvaluators = strategy.aggregate(List.of(
                result("x", 30.0, 1.0, Recommendation.ALLOW, EvaluatorStatus.OK)), null);

        assertThat(withBlankNames.getAggregatedScore()).isCloseTo(30.0, within(1e-9));
        assertThat(withoutEvaluators.getAggregatedScore()).isCloseTo(30.0, within(1e-9));
    }

    @Test
    void majorityRecommendation_isTheMostFrequent() {
        RiskAssessment assessment = strategy.aggregate(List.of(
                result("heavy", 10.0, 1.0, Recommendation.ALLOW, EvaluatorStatus.OK),
                result("light", 10.0, 1.0, Recommendation.ALLOW, EvaluatorStatus.OK),
                result("x", 10.0, 1.0, Recommendation.DENY, EvaluatorStatus.OK)), WEIGHTS);

        assertThat(assessment.getMajorityRecommendation()).isEqualTo(Recommendation.ALLOW);
    }

    @Test
    void missingRecommendation_countsAsChallenge() {
        RiskAssessment assessment = strategy.aggregate(List.of(
                result("heavy", 50.0, 1.0, null, EvaluatorStatus.OK)), WEIGHTS);

        assertThat(assessment.getMajorityRecommendation()).isEqualTo(Recommendation.CHALLENGE);
    }

    @Test
    void tie_isBrokenByTheAggregatedScore() {
        assertThat(tieWithScore(90.0)).isEqualTo(Recommendation.DENY);
        assertThat(tieWithScore(75.0)).isEqualTo(Recommendation.DENY);
        assertThat(tieWithScore(25.0)).isEqualTo(Recommendation.ALLOW);
        assertThat(tieWithScore(10.0)).isEqualTo(Recommendation.ALLOW);
        assertThat(tieWithScore(50.0)).isEqualTo(Recommendation.CHALLENGE);
    }

    private Recommendation tieWithScore(double score) {
        return strategy.aggregate(List.of(
                result("heavy", score, 1.0, Recommendation.ALLOW, EvaluatorStatus.OK),
                result("light", score, 1.0, Recommendation.DENY, EvaluatorStatus.OK)), WEIGHTS)
                .getMajorityRecommendation();
    }
}
