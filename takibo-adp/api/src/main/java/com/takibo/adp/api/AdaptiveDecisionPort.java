package com.takibo.adp.api;

public interface AdaptiveDecisionPort {
    
    DecisionResponse evaluate(DecisionRequest request);
}
