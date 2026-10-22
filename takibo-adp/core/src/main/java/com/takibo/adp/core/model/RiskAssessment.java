package com.takibo.adp.core.model;

import lombok.Builder;
import lombok.Getter;
import com.takibo.adp.core.evaluator.Recommendation;

@Getter
@Builder
public class RiskAssessment {
    
    private final double aggregatedScore;
    private final double confidence;
    private final double uncertainty;
    private final Recommendation majorityRecommendation;
    private final int evaluatorCount;
    private final double healthRatio;
}
