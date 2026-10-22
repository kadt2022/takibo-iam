package com.takibo.adp.spring.adapter;

import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.core.port.BehaviorProfileWriter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoopBehaviorProfileWriter implements BehaviorProfileWriter {
    
    @Override
    public void recordSuccessfulAccess(String subjectId, DecisionRequest request) {
        log.trace("NoopBehaviorProfileWriter: skip recording for {}", subjectId);
    }
    
    @Override
    public void recordFailedAttempt(String subjectId, DecisionRequest request) {
        log.trace("NoopBehaviorProfileWriter: skip recording failure for {}", subjectId);
    }
}
