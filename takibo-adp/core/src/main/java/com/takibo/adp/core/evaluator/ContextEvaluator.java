package com.takibo.adp.core.evaluator;

import com.takibo.adp.core.model.AccessContext;

public interface ContextEvaluator {
    
    String getName();
    
    double getWeight();
    
    boolean isEnabled();
    
    EvaluatorResult evaluate(AccessContext context);
}
