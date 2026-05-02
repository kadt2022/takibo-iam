package com.takibo.audit.aspect;

import com.takibo.audit.annotations.TriggerAlertOnFailure;

import com.takibo.audit.infrastructure.entity.AuditEvent;
import com.takibo.audit.infrastructure.service.FailureCounterService;
import com.takibo.audit.infrastructure.service.SecurityAlertService;
import com.takibo.audit.spi.AuditActorProvider;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Arrays;


@Aspect
@Component
@RequiredArgsConstructor
public class AlertTriggerAspect {
    private final SecurityAlertService alertService;
    private final FailureCounterService failureCounter;
    private final AuditActorProvider auditActorProvider;

    @AfterThrowing(
            pointcut = "@annotation(triggerConfig)",
            throwing = "ex"
    )
    public void handleFailure(JoinPoint jp, TriggerAlertOnFailure triggerConfig, Exception ex) {
        String compositeKey = String.format("%s-%s",
                jp.getSignature().toShortString(),
                ex.getClass().getSimpleName()
        );

        if (shouldTrigger(triggerConfig, ex) &&
                failureCounter.reachedThreshold(compositeKey, triggerConfig.threshold())) {

            AuditEvent event = buildAlertEvent(jp, triggerConfig, ex);
            alertService.triggerAlert(event);
        }
    }

    private boolean shouldTrigger(TriggerAlertOnFailure config, Exception ex) {
        return Arrays.stream(config.triggerOn())
                .anyMatch(exceptionClass -> exceptionClass.isInstance(ex));
    }

    private AuditEvent buildAlertEvent(JoinPoint jp, TriggerAlertOnFailure config, Exception ex) {
        AuditActorProvider.AuditActor actor = auditActorProvider.currentActor().orElse(null);
        return AuditEvent.builder()
                .auditType(config.auditType())
                .status("FAILED")
                .error(ex.getMessage())
                .entityType(resolveEntityType(jp))
                .actorAccountId(actor != null && actor.accountId() != null ? actor.accountId().toString() : null)
                .userId(actor != null && actor.userId() != null ? actor.userId().toString() : null)
                .actorUserId(actor != null && actor.userId() != null ? actor.userId().toString() : null)
                .orgId(actor != null && actor.orgId() != null ? actor.orgId().toString() : null)
                .spaceId(actor != null && actor.spaceId() != null ? actor.spaceId().toString() : null)
                .actorType(actor != null ? actor.actorType() : null)
                .actorSource(actor != null ? actor.actorSource() : null)
                .clientIp(resolveClientIp())
                .timestamp(Instant.now())
                .build();
    }

    private String resolveEntityType(JoinPoint jp) {
        return jp.getSignature().getName();
    }


    private String resolveClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getRemoteAddr();
        }
        return "UNKNOWN";
    }
}
