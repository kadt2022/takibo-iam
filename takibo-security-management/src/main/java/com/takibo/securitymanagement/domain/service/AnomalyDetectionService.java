package com.takibo.securitymanagement.domain.service;


import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.Environment;
import com.takibo.securitymanagement.domain.model.Subject;
import org.springframework.stereotype.Service;

@Service
public class AnomalyDetectionService {

    public boolean isSuspicious(Subject subject, Action action, Environment env) {
        // v1 : pas encore de vraie détection, toujours false
        return false;
    }
}
