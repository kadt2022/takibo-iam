package com.takibo.adp.core.engine;

import com.takibo.adp.api.*;
import com.takibo.adp.core.evaluator.*;
import com.takibo.adp.core.model.*;
import com.takibo.adp.core.port.ThresholdPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class DecisionEngine {
    
    private final List<ContextEvaluator> evaluators;
    private final ThresholdPolicy thresholdPolicy;
    private final AdpExecutor executor;
    private final AggregationStrategy aggregationStrategy;
    
    public DecisionResponse evaluate(DecisionRequest request) {
        long startTime = System.nanoTime();
        String decisionId = UUID.randomUUID().toString();
        
        log.debug("ADP evaluation START | id={} subject={} resource={}", 
            decisionId, request.subjectId(), request.resourcePath());
        
        AccessContext context = AccessContext.fromRequest(request);
        
        List<EvaluatorResult> results = executor.executeWithTimeout(
            evaluators, 
            context, 
            request.timeoutMs()
        );
        
        Thresholds thresholds = thresholdPolicy.calculate(request);
        
        RiskAssessment assessment = aggregationStrategy.aggregate(results, evaluators);
        
        Decision finalDecision = determineDecision(assessment, thresholds);
        
        String explanation = generateExplanation(results, assessment, thresholds, finalDecision);
        
        int expectedCount = (int) evaluators.stream().filter(ContextEvaluator::isEnabled).count();
        DecisionStatus status = determineStatus(results, expectedCount);
        
        long executionTime = (System.nanoTime() - startTime) / 1_000_000;
        
        log.debug("ADP evaluation COMPLETE | id={} decision={} risk={} time={}ms",
            decisionId, finalDecision, String.format("%.1f", assessment.getAggregatedScore()), executionTime);
        
        return new DecisionResponse(
            decisionId,
            finalDecision,
            assessment.getAggregatedScore(),
            assessment.getConfidence(),
            assessment.getUncertainty(),
            explanation,
            thresholds,
            extractTopFactors(results, 5),
            status,
            context.getTimestamp(),
            executionTime
        );
    }
    
    private Decision determineDecision(RiskAssessment assessment, Thresholds thresholds) {
        double score = assessment.getAggregatedScore();
        double confidence = assessment.getConfidence();
        
        if (confidence < 0.20) {
            log.debug("Low confidence ({}), forcing CHALLENGE", String.format("%.2f", confidence));
            return Decision.CHALLENGE;
        }
        
        if (score >= thresholds.denyThreshold() && confidence >= 0.7) {
            return Decision.DENY;
        }
        
        if (score >= thresholds.challengeThreshold()) {
            return Decision.CHALLENGE;
        }
        
        return Decision.ALLOW;
    }
    
    private String generateExplanation(
        List<EvaluatorResult> results,
        RiskAssessment assessment,
        Thresholds thresholds,
        Decision decision
    ) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("Risk score: %.1f/100 (confidence: %.2f). ",
            assessment.getAggregatedScore(),
            assessment.getConfidence()));
        
        sb.append(String.format("Thresholds: deny=%.0f, challenge=%.0f (%s). ",
            thresholds.denyThreshold(),
            thresholds.challengeThreshold(),
            thresholds.reason()));
        
        List<EvaluatorResult> topContributors = results.stream()
            .filter(r -> r.getStatus() == EvaluatorStatus.OK)
            .filter(r -> r.getRiskScore() > 15.0)
            .sorted(Comparator.comparingDouble(EvaluatorResult::getRiskScore).reversed())
            .limit(3)
            .toList();
        
        if (!topContributors.isEmpty()) {
            sb.append("Main factors: ");
            for (EvaluatorResult r : topContributors) {
                sb.append(String.format("%s (%.0f): %s; ",
                    r.getEvaluatorName(),
                    r.getRiskScore(),
                    r.getReason()));
            }
        }
        
        long errorCount = results.stream()
            .filter(r -> r.getStatus() != EvaluatorStatus.OK)
            .count();
        if (errorCount > 0) {
            sb.append(String.format("Warning: %d evaluators incomplete. ", errorCount));
        }
        
        return sb.toString().trim();
    }


    private DecisionStatus determineStatus(List<EvaluatorResult> results, int expectedCount) {
        if (results == null || results.isEmpty()) {
            return DecisionStatus.ERROR;
        }

        long timeoutCount = results.stream()
                .filter(r -> r.getStatus() == EvaluatorStatus.TIMEOUT)
                .count();

        if (timeoutCount == results.size()) {
            return DecisionStatus.TIMEOUT;
        }

        long okCount = results.stream()
                .filter(r -> r.getStatus() == EvaluatorStatus.OK)
                .count();

        if (okCount == 0) {
            return DecisionStatus.ERROR;
        }

        if (expectedCount > 0 && okCount == expectedCount) {
            return DecisionStatus.OK;
        }

        return DecisionStatus.PARTIAL;
    }

    private List<DecisionFactor> extractTopFactors(List<EvaluatorResult> results, int limit) {
        return results.stream()
            .sorted(Comparator.comparingDouble(EvaluatorResult::getRiskScore).reversed())
            .limit(limit)
            .map(r -> new DecisionFactor(
                r.getEvaluatorName(),
                r.getRiskScore(),
                r.getConfidence(),
                r.getReason(),
                r.getStatus()
            ))
            .collect(Collectors.toList());
    }
}
