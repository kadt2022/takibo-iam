package com.takibo.audit.domain;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 1er
@Slf4j
public class PathVariableIdResolutionStrategy implements IdResolutionStrategy {

    @Override
    public Optional<String> resolve(JoinPoint joinPoint, String expression, Object result) {
        if (expression == null || !expression.startsWith("#path.")) return Optional.empty();

        String paramName = expression.substring("#path.".length());
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof PathVariable pathVar && pathVar.value().equals(paramName)) {
                    Object arg = args[i];
                    return arg != null ? Optional.of(arg.toString()) : Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
}
