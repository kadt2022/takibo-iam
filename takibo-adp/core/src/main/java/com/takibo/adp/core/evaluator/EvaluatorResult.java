package com.takibo.adp.core.evaluator;

import com.takibo.adp.api.EvaluatorStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvaluatorResult {
    
    private final String evaluatorName;
    private final double riskScore;
    private final double confidence;
    private final String reason;
    private final Recommendation recommendation;
    private final EvaluatorStatus status;
}
