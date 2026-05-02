package com.takibo.audit.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntityIdResolver {

    private static final String UNKNOWN = "UNKNOWN";
    private final List<IdResolutionStrategy> strategies;

    public String resolveId(JoinPoint joinPoint, String expression, Object result) {
        strategies.sort(AnnotationAwareOrderComparator.INSTANCE);
        for (IdResolutionStrategy s : strategies) {
            try {
                var candidate = s.resolve(joinPoint, expression, result);
                if (candidate.isPresent() && !UNKNOWN.equals(candidate.get())) {
                    return candidate.get();
                }
            } catch (Exception e) {
                log.debug("Strategy {} failed: {}", s.getClass().getSimpleName(), e.toString());
            }
        }
        return UNKNOWN;
    }
}
