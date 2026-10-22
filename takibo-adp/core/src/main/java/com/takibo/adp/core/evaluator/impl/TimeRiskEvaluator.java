package com.takibo.adp.core.evaluator.impl;

import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.*;
import com.takibo.adp.core.model.AccessContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TimeRiskEvaluator implements ContextEvaluator {
    
    private final boolean enabled;
    
    @Override
    public String getName() {
        return "TimeRiskEvaluator";
    }
    
    @Override
    public double getWeight() {
        return 0.10;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public EvaluatorResult evaluate(AccessContext context) {
        if (context.getTimestamp() == null) {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(50.0)
                .confidence(0.3)
                .reason("No timestamp available")
                .recommendation(Recommendation.CHALLENGE)
                .status(EvaluatorStatus.NO_DATA)
                .build();
        }
        
        boolean businessHours = context.isDuringBusinessHours();
        
        if (businessHours) {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(15.0)
                .confidence(0.8)
                .reason("Access during typical business hours")
                .recommendation(Recommendation.ALLOW)
                .status(EvaluatorStatus.OK)
                .build();
        } else {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(55.0)
                .confidence(0.7)
                .reason("Access outside typical business hours")
                .recommendation(Recommendation.CHALLENGE)
                .status(EvaluatorStatus.OK)
                .build();
        }
    }
}
