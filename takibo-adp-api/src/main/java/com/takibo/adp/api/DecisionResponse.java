package com.takibo.adp.api;

import java.time.Instant;
import java.util.List;

public record DecisionResponse(
    String decisionId,
    Decision decision,
    double riskScore,
    double confidence,
    double uncertainty,
    String explanation,
    Thresholds thresholds,
    List<DecisionFactor> topFactors,
    DecisionStatus status,
    Instant timestamp,
    long executionTimeMs
) {
    public boolean isAllowed() {
        return decision == Decision.ALLOW;
    }
    
    public boolean isDenied() {
        return decision == Decision.DENY;
    }
    
    public boolean requiresChallenge() {
        return decision == Decision.CHALLENGE;
    }
}
