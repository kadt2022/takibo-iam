package com.takibo.adp.core.evaluator.impl;

import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.*;
import com.takibo.adp.core.model.AccessContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class NetworkRiskEvaluator implements ContextEvaluator {
    
    private final boolean enabled;
    
    @Override
    public String getName() {
        return "NetworkRiskEvaluator";
    }
    
    @Override
    public double getWeight() {
        return 0.15;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public EvaluatorResult evaluate(AccessContext context) {
        if (context.isHighRiskNetwork()) {
            String type = determineNetworkType(context);
            
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(80.0)
                .confidence(0.9)
                .reason("High risk network detected: " + type)
                .recommendation(Recommendation.CHALLENGE)
                .status(EvaluatorStatus.OK)
                .build();
        }
        
        return EvaluatorResult.builder()
            .evaluatorName(getName())
            .riskScore(10.0)
            .confidence(0.9)
            .reason("Network appears normal")
            .recommendation(Recommendation.ALLOW)
            .status(EvaluatorStatus.OK)
            .build();
    }
    
    private String determineNetworkType(AccessContext context) {
        if (Boolean.TRUE.equals(context.getIsTor())) {
            return "Tor";
        }
        if (Boolean.TRUE.equals(context.getIsVpn())) {
            return "VPN";
        }
        if (Boolean.TRUE.equals(context.getIsProxy())) {
            return "Proxy";
        }
        return "Unknown anonymizer";
    }
}
