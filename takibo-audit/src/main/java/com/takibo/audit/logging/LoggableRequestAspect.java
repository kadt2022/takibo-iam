package com.takibo.audit.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

/**
 * Aspect qui intercepte toutes les classes annotées avec @LoggableRequest.
 * Il logue le début et la fin de chaque méthode avec les arguments et la durée d'exécution.
 */
@Aspect
@Component
public class LoggableRequestAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggableRequestAspect.class);

    @Around("@within(com.takibo.audit.logging.LoggableRequest)")
    public Object logRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
        }

        String clientIp = MDC.get("clientIp");
        String userAgent = MDC.get("userAgent");
        String clientApp = MDC.get("clientApp");

        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();

        log.info("Trace [{}] | Client IP [{}] | User-Agent [{}] | Client-App [{}] | Début de {}.{} avec arguments: {}",
                traceId, clientIp, userAgent, clientApp, className, methodName, Arrays.toString(joinPoint.getArgs()));

        Object result = joinPoint.proceed();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Trace [{}] | Fin de {}.{} en {} ms",
                traceId, className, methodName, duration);

        return result;
    }
}
