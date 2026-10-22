package com.takibo.adp.spring.adapter;

import com.takibo.adp.api.*;
import com.takibo.adp.core.engine.DecisionEngine;
import com.takibo.adp.core.port.BehaviorProfileWriter;
import com.takibo.adp.spring.config.AdpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SpringAdaptiveDecisionService implements AdaptiveDecisionPort {
    
    private final DecisionEngine engine;
    private final BehaviorProfileWriter profileWriter;
    private final AdpProperties properties;
    
    @Override
    public DecisionResponse evaluate(DecisionRequest request) {
        if (!properties.isEnabled()) {
            log.debug("ADP disabled, returning neutral decision");
            return createNeutralDecision(request);
        }
        
        try {
            DecisionResponse response = engine.evaluate(request);
            
            if (response.isAllowed()) {
                profileWriter.recordSuccessfulAccess(request.subjectId(), request);
            } else {
                profileWriter.recordFailedAttempt(request.subjectId(), request);
            }
            
            return response;
        } catch (Exception e) {
            log.error("ADP evaluation failed for subject={}", request.subjectId(), e);
            return createErrorDecision(request, e);
        }
    }
    
    private DecisionResponse createNeutralDecision(DecisionRequest request) {
        return new DecisionResponse(
            "disabled",
            Decision.ALLOW,
            50.0,
            0.5,
            0.5,
            "ADP is disabled",
            new Thresholds(75.0, 40.0, "disabled"),
            java.util.List.of(),
            DecisionStatus.OK,
            request.timestamp(),
            0L
        );
    }
    
    private DecisionResponse createErrorDecision(DecisionRequest request, Exception error) {
        return new DecisionResponse(
            "error",
            Decision.CHALLENGE,
            50.0,
            0.0,
            1.0,
            "ADP error: " + error.getMessage(),
            new Thresholds(75.0, 40.0, "error fallback"),
            java.util.List.of(),
            DecisionStatus.ERROR,
            request.timestamp(),
            0L
        );
    }
}
