package com.takibo.audit.aspect;

import com.takibo.audit.annotations.Audit;
import com.takibo.audit.core.AuditEventBuilder;
import com.takibo.audit.infrastructure.config.AuditContext;
import com.takibo.audit.infrastructure.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private final AuditService auditService;
    private final AuditEventBuilder eventBuilder;

    @Around("@annotation(audit)")
    public Object auditMethodExecution(ProceedingJoinPoint pjp, Audit audit) throws Throwable {
        AuditContext context = AuditContext.getCurrent();
        boolean ownedContext = false;
        if (context == null) {
            context = AuditContext.init();
            ownedContext = true;
        }
        try {
            Object result = pjp.proceed();
            logSuccessfulExecution(pjp, audit, result, context);
            return result;
        } catch (Exception e) {
            logFailedExecution(pjp, audit, e, context);
            throw e;
        } finally {
            if (ownedContext) {
                AuditContext.clear();
            }
        }
    }

    private void logSuccessfulExecution(ProceedingJoinPoint pjp, Audit audit, Object result, AuditContext context) {
        try {
            auditService.save(eventBuilder.buildEvent(
                    pjp, audit, result, STATUS_SUCCESS, null, context));
        } catch (AuditEventBuilder.AuditEventBuildException e) {
            log.error("Failed to log successful audit event", e);
        }
    }

    private void logFailedExecution(ProceedingJoinPoint pjp, Audit audit, Exception e, AuditContext context) {
        try {
            auditService.save(eventBuilder.buildEvent(
                    pjp, audit, null, STATUS_FAILED, e.getMessage(), context));
        } catch (AuditEventBuilder.AuditEventBuildException ex) {
            log.error("Failed to log failed audit event", ex);
        }
    }
}
