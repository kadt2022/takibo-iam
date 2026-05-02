package com.takibo.adp.core.port;

import com.takibo.adp.api.DecisionRequest;

public interface BehaviorProfileWriter {
    
    void recordSuccessfulAccess(String subjectId, DecisionRequest request);
    
    void recordFailedAttempt(String subjectId, DecisionRequest request);
}
