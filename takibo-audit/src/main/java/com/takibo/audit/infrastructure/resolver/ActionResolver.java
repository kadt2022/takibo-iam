package com.takibo.audit.infrastructure.resolver;

import com.takibo.audit.annotations.LogAction;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class ActionResolver {

    public String resolveAction(JoinPoint jp) {
        Method method = ((MethodSignature) jp.getSignature()).getMethod();
        return method.getAnnotation(LogAction.class) != null
                ? method.getAnnotation(LogAction.class).value()
                : method.getName();
    }

    public String resolveAction(JoinPoint jp, LogAction annotation) {
        return !annotation.value().isEmpty()
                ? annotation.value()
                : jp.getSignature().getName();
    }
}