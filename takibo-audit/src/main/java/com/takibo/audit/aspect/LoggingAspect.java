package com.takibo.audit.aspect;

import com.takibo.audit.annotations.LogAction;
import com.takibo.audit.domain.LogEvent;
import com.takibo.audit.infrastructure.config.AuditContext;
import com.takibo.audit.infrastructure.resolver.ActionResolver;
import com.takibo.audit.infrastructure.service.LogDispatcher;
import com.takibo.audit.infrastructure.service.MaskingService;
import com.takibo.audit.spi.AuditActorProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class LoggingAspect {
    private final LogDispatcher logDispatcher;
    private final ActionResolver actionResolver;
    private final MaskingService maskingService;
    private final AuditActorProvider auditActorProvider;

    @Around("@annotation(logAction)")
    public Object logMethod(ProceedingJoinPoint pjp, LogAction logAction) throws Throwable {
        AuditContext context = AuditContext.getCurrent();
        boolean ownedContext = false;
        if (context == null) {
            context = AuditContext.init();
            ownedContext = true;
        }
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        AuditActorProvider.AuditActor actor = auditActorProvider.currentActor().orElse(null);

        LogEvent.LogEventBuilder eventBuilder = LogEvent.builder()
                .timestamp(Instant.now())
                .action(actionResolver.resolveAction(pjp, logAction))
                .level(logAction.level())
                .method(pjp.getSignature().getName())
                .userId(actor != null && actor.userId() != null ? actor.userId().toString() : null)
                .actorAccountId(actor != null && actor.accountId() != null ? actor.accountId().toString() : null)
                .actorUserId(actor != null && actor.userId() != null ? actor.userId().toString() : null)
                .orgId(actor != null && actor.orgId() != null ? actor.orgId().toString() : null)
                .spaceId(actor != null && actor.spaceId() != null ? actor.spaceId().toString() : null)
                .actorType(actor != null ? actor.actorType() : null)
                .actorSource(actor != null ? actor.actorSource() : null)
                .params(logAction.trackParams() ? maskingService.mask(method, pjp.getArgs()) : null);

        if (context != null) {
            eventBuilder
                    .traceId(context.getTraceId())
                    .clientIp(context.getClientIp())
                    .userAgent(context.getUserAgent())
                    .clientApp(context.getClientApp());
        }

        LogEvent event = eventBuilder.build();

        try {
            Object result = pjp.proceed();
            event.setStatus("SUCCESS");
            return result;
        } catch (Exception e) {
            event.setStatus("FAILED");
            event.setError(e.getMessage());
            throw e;
        } finally {
            logDispatcher.dispatch(event);
            if (ownedContext) {
                AuditContext.clear();
            }
        }
    }
}
