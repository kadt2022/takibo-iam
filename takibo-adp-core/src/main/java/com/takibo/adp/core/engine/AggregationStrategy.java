package com.takibo.adp.core.engine;

import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.ContextEvaluator;
import com.takibo.adp.core.evaluator.EvaluatorResult;
import com.takibo.adp.core.evaluator.Recommendation;
import com.takibo.adp.core.model.RiskAssessment;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class AggregationStrategy {

    private static final double DEFAULT_WEIGHT = 0.1;
    private static final double DEFAULT_SCORE = 50.0;

    public RiskAssessment aggregate(
            List<EvaluatorResult> results,
            List<ContextEvaluator> allEvaluators
    ) {
        if (results == null || results.isEmpty()) {
            return RiskAssessment.builder()
                    .aggregatedScore(DEFAULT_SCORE)
                    .confidence(0.0)
                    .uncertainty(1.0)
                    .majorityRecommendation(Recommendation.CHALLENGE)
                    .evaluatorCount(0)
                    .healthRatio(0.0)
                    .build();
        }

        Map<String, Double> weightsByName = buildWeights(allEvaluators);

        double weightedSum = 0.0;
        double weightSum = 0.0;

        double confidenceSum = 0.0;
        int okCount = 0;

        Map<Recommendation, Integer> recommendationCounts = new HashMap<>();

        for (EvaluatorResult r : results) {
            if (r == null) {
                continue;
            }

            double confidence = clamp01(r.getConfidence());
            double weight = weightsByName.getOrDefault(r.getEvaluatorName(), DEFAULT_WEIGHT);

            confidenceSum += confidence;

            if (r.getStatus() == EvaluatorStatus.OK) {
                okCount++;
            }

            recommendationCounts.merge(
                    r.getRecommendation() != null ? r.getRecommendation() : Recommendation.CHALLENGE,
                    1,
                    Integer::sum
            );

            // Weighted average of scores, but only where confidence contributes
            weightedSum += r.getRiskScore() * weight * confidence;
            weightSum += weight * confidence;
        }

        double aggregatedScore = (weightSum > 0.0) ? (weightedSum / weightSum) : DEFAULT_SCORE;

        double avgConfidence = confidenceSum / results.size();
        double healthRatio = (double) okCount / results.size();

        double effectiveConfidence = clamp01(avgConfidence * healthRatio);
        double uncertainty = 1.0 - effectiveConfidence;

        Recommendation majority = majorityWithTieBreak(recommendationCounts, aggregatedScore);

        log.debug("Aggregation: score={} confidence={} health={}",
                String.format("%.1f", aggregatedScore),
                String.format("%.2f", effectiveConfidence),
                String.format("%.2f", healthRatio)
        );

        return RiskAssessment.builder()
                .aggregatedScore(aggregatedScore)
                .confidence(effectiveConfidence)
                .uncertainty(uncertainty)
                .majorityRecommendation(majority)
                .evaluatorCount(results.size())
                .healthRatio(healthRatio)
                .build();
    }

    private Map<String, Double> buildWeights(List<ContextEvaluator> evaluators) {
        Map<String, Double> map = new HashMap<>();
        if (evaluators == null) {
            return map;
        }
        for (ContextEvaluator e : evaluators) {
            if (e != null && e.getName() != null && !e.getName().isBlank()) {
                map.put(e.getName(), e.getWeight());
            }
        }
        return map;
    }

    private Recommendation majorityWithTieBreak(Map<Recommendation, Integer> counts, double aggregatedScore) {
        if (counts == null || counts.isEmpty()) {
            return Recommendation.CHALLENGE;
        }

        int max = -1;
        Recommendation best = Recommendation.CHALLENGE;
        boolean tie = false;

        for (Map.Entry<Recommendation, Integer> e : counts.entrySet()) {
            int v = e.getValue() != null ? e.getValue() : 0;
            if (v > max) {
                max = v;
                best = e.getKey();
                tie = false;
            } else if (v == max) {
                tie = true;
            }
        }

        if (!tie) {
            return best;
        }

        // Tie-break: prefer CHALLENGE for ambiguity,
        // but if aggregatedScore is very low/high, prefer ALLOW/DENY.
        if (aggregatedScore >= 75.0) return Recommendation.DENY;
        if (aggregatedScore <= 25.0) return Recommendation.ALLOW;
        return Recommendation.CHALLENGE;
    }

    private double clamp01(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }
}
