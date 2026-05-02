package com.takibo.audit.core;

import com.takibo.audit.domain.EntityIdResolver;
import com.takibo.audit.infrastructure.entity.AuditEvent;
import com.takibo.audit.infrastructure.config.AuditContext;
import com.takibo.audit.infrastructure.resolver.ActionResolver;
import com.takibo.audit.infrastructure.service.MaskingService;
import com.takibo.audit.spi.AuditActorProvider;
import com.takibo.audit.annotations.Audit;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;

@RequiredArgsConstructor
public class AuditEventBuilder {
    private final EntityIdResolver entityResolver;
    private final MaskingService maskingService;
    private final ActionResolver actionResolver;
    private final AuditActorProvider auditActorProvider;

    public AuditEvent buildEvent(JoinPoint jp, Audit audit, Object result,
                                 String status, String error, AuditContext context) {
        try {
            Method method = getMethodFromJoinPoint(jp);
            AuditActorProvider.AuditActor actor = auditActorProvider.currentActor().orElse(null);

            return AuditEvent.builder()
                    .timestamp(Instant.now())
                    .auditType(audit.type())
                    .entityType(audit.entityType())
                    .entityId(resolveEntityId(jp, audit, result))
                    .actorAccountId(actor != null && actor.accountId() != null ? actor.accountId().toString() : null)
                    .userId(actor != null && actor.userId() != null ? actor.userId().toString() : null)
                    .actorUserId(actor != null && actor.userId() != null ? actor.userId().toString() : null)
                    .orgId(actor != null && actor.orgId() != null ? actor.orgId().toString() : null)
                    .spaceId(actor != null && actor.spaceId() != null ? actor.spaceId().toString() : null)
                    .actorType(actor != null ? actor.actorType() : null)
                    .actorSource(actor != null ? actor.actorSource() : null)
                    .action(resolveAction(jp))
                    .status(status)
                    .error(error)
                    .details(buildExecutionDetails(jp))
                    .params(maskParameters(method, jp))
                    .traceId(context.getTraceId())
                    .clientIp(context.getClientIp())
                    .userAgent(context.getUserAgent())
                    .clientApp(context.getClientApp())
                    .durationMs(calculateDuration(context))
                    .httpMethod(resolveHttpMethod(method))
                    .endpoint(resolveEndpoint(jp))
                    .build();
        } catch (Exception e) {
            throw new AuditEventBuildException("Failed to build audit event", e);
        }
    }

    private Method getMethodFromJoinPoint(JoinPoint jp) {
        return ((MethodSignature) jp.getSignature()).getMethod();
    }

    private String resolveEntityId(JoinPoint jp, Audit audit, Object result) {
        return entityResolver.resolveId(jp, audit.entityIdParam(), result);
    }

    private String resolveAction(JoinPoint jp) {
        return actionResolver.resolveAction(jp);
    }

    private Map<String, Object> maskParameters(Method method, JoinPoint jp) {
        return maskingService.mask(method, jp.getArgs());
    }

    private long calculateDuration(AuditContext context) {
        return System.currentTimeMillis() - context.getStartTime();
    }

    private String resolveHttpMethod(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) return "GET";
        if (method.isAnnotationPresent(PostMapping.class)) return "POST";
        if (method.isAnnotationPresent(PutMapping.class)) return "PUT";
        if (method.isAnnotationPresent(DeleteMapping.class)) return "DELETE";
        if (method.isAnnotationPresent(PatchMapping.class)) return "PATCH";
        return "UNKNOWN";
    }

    private String resolveEndpoint(JoinPoint jp) {
        Method method = getMethodFromJoinPoint(jp);
        String basePath = resolveBasePath(jp);
        String methodPath = resolveMethodPath(method);

        return normalizePath(basePath + methodPath);
    }

    private String resolveBasePath(JoinPoint jp) {
        Class<?> declaringClass = jp.getSignature().getDeclaringType();
        if (declaringClass.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = declaringClass.getAnnotation(RequestMapping.class);
            return getFirstMappingValue(mapping.value());
        }
        return "";
    }

    private String resolveMethodPath(Method method) {
        if (method.isAnnotationPresent(RequestMapping.class)) {
            return getFirstMappingValue(method.getAnnotation(RequestMapping.class).value());
        }
        if (method.isAnnotationPresent(GetMapping.class)) {
            return getFirstMappingValue(method.getAnnotation(GetMapping.class).value());
        }
        if (method.isAnnotationPresent(PostMapping.class)) {
            return getFirstMappingValue(method.getAnnotation(PostMapping.class).value());
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            return getFirstMappingValue(method.getAnnotation(PutMapping.class).value());
        }
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            return getFirstMappingValue(method.getAnnotation(DeleteMapping.class).value());
        }
        if (method.isAnnotationPresent(PatchMapping.class)) {
            return getFirstMappingValue(method.getAnnotation(PatchMapping.class).value());
        }
        return "";
    }

    private String getFirstMappingValue(String[] values) {
        return (values != null && values.length > 0) ? values[0] : "";
    }

    private String normalizePath(String path) {
        return path.replaceAll("//+", "/");
    }

    private String buildExecutionDetails(JoinPoint jp) {
        Method method = getMethodFromJoinPoint(jp);
        return String.format("%s.%s executed with %d params",
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                jp.getArgs().length);
    }

    public static class AuditEventBuildException extends RuntimeException {
        public AuditEventBuildException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
