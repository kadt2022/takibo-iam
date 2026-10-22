package com.takibo.adp.core.engine;

import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.ContextEvaluator;
import com.takibo.adp.core.evaluator.EvaluatorResult;
import com.takibo.adp.core.evaluator.Recommendation;
import com.takibo.adp.core.model.AccessContext;

/**
 * Évaluateur de test au résultat fixe : rend les décisions du moteur déterministes.
 */
final class StubEvaluator implements ContextEvaluator {

    private final String name;
    private final double weight;
    private final boolean enabled;
    private final EvaluatorResult result;

    private StubEvaluator(String name, double weight, boolean enabled, EvaluatorResult result) {
        this.name = name;
        this.weight = weight;
        this.enabled = enabled;
        this.result = result;
    }

    static StubEvaluator ok(String name, double weight, double score, double confidence, Recommendation recommendation) {
        return new StubEvaluator(name, weight, true,
                result(name, score, confidence, recommendation, EvaluatorStatus.OK));
    }

    static StubEvaluator withStatus(String name, double weight, double score, double confidence, EvaluatorStatus status) {
        return new StubEvaluator(name, weight, true,
                result(name, score, confidence, Recommendation.CHALLENGE, status));
    }

    static StubEvaluator disabled(String name) {
        return new StubEvaluator(name, 0.1, false,
                result(name, 99.0, 1.0, Recommendation.DENY, EvaluatorStatus.OK));
    }

    static EvaluatorResult result(String name, double score, double confidence,
                                  Recommendation recommendation, EvaluatorStatus status) {
        return EvaluatorResult.builder()
                .evaluatorName(name)
                .riskScore(score)
                .confidence(confidence)
                .reason(name + " reason")
                .recommendation(recommendation)
                .status(status)
                .build();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getWeight() {
        return weight;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public EvaluatorResult evaluate(AccessContext context) {
        return result;
    }
}
