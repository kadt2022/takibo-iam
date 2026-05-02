package com.takibo.adp.api;

public record DecisionFactor(
    String evaluatorName,
    double riskScore,
    double confidence,
    String reason,
    EvaluatorStatus status
) {}
