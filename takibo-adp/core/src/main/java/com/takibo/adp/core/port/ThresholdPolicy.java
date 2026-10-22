package com.takibo.adp.core.port;

import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.api.Thresholds;

public interface ThresholdPolicy {
    
    Thresholds calculate(DecisionRequest request);
}
