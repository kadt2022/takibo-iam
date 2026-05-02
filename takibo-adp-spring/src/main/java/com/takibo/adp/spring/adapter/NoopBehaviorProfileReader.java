package com.takibo.adp.spring.adapter;

import com.takibo.adp.api.BehaviorProfileView;
import com.takibo.adp.core.port.BehaviorProfileReader;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class NoopBehaviorProfileReader implements BehaviorProfileReader {
    
    @Override
    public Optional<BehaviorProfileView> findBySubjectId(String subjectId) {
        log.trace("NoopBehaviorProfileReader: no profile for {}", subjectId);
        return Optional.empty();
    }
}
