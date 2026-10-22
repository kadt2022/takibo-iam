package com.takibo.adp.core.port;

import com.takibo.adp.api.BehaviorProfileView;

import java.util.Optional;

public interface BehaviorProfileReader {
    
    Optional<BehaviorProfileView> findBySubjectId(String subjectId);
}
