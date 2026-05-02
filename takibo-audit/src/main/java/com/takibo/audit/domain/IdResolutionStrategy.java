package com.takibo.audit.domain;

import org.aspectj.lang.JoinPoint;
import java.util.Optional;

public interface IdResolutionStrategy {
    Optional<String> resolve(JoinPoint joinPoint, String expression, Object result);
}
