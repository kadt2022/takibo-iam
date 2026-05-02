package com.takibo.identitycore.integration.space.annotations;

import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import com.takibo.identitycore.domain.status.SpaceGuardStatus;
import com.takibo.identitycore.domain.exception.SpaceGuardException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.domain.status.SpaceOperationalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.stream.Stream;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class SpaceActiveAspect {

    private final SpaceStatusCheckerCase spaceStatusCheckerCase;

    @Value("${takibo.policy.aop-require-active-enabled:true}")
    private boolean enabled;

    @Before("@within(annotations.aspect.application.com.takibo.identitycore.RequireActiveSpace) || " +
            "@annotation(annotations.aspect.application.com.takibo.identitycore.RequireActiveSpace)")
    public void ensureActive(JoinPoint jp) {
        if (!enabled) return;

        RequireActiveSpace cfg = resolveAnnotation(jp);
        String[] keys = (cfg != null && cfg.pathVarKeys().length > 0)
                ? cfg.pathVarKeys()
                : new String[]{"spaceId", "sid"};

        UUID spaceId = resolveSpaceId(jp, keys);
        if (spaceId == null) {
            throw new IllegalStateException("@RequireActiveSpace: spaceId introuvable (pathVar/args)");
        }

        var statusOpt = spaceStatusCheckerCase.findStatus(spaceId);
        if (statusOpt.isEmpty()) {
            throw new SpaceNotFoundException(spaceId);
        }

        SpaceOperationalStatus status = statusOpt.get();
        log.debug("[RequireActiveSpace] spaceId={} status={}", spaceId, status);

        switch (status) {
            case ACTIVE -> { /* Ok */ }
            case SUSPENDED ->
                    throw new SpaceGuardException(SpaceGuardStatus.SPACE_SUSPENDED, spaceId, "Space is SUSPENDED");
            case DISABLED ->
                    throw new SpaceGuardException(SpaceGuardStatus.SPACE_DISABLED, spaceId, "Space is DISABLED");
            default ->
                    throw new SpaceGuardException(SpaceGuardStatus.SPACE_STATUS_UNKNOWN, spaceId, "Space status unknown");
        }
    }

    private RequireActiveSpace resolveAnnotation(JoinPoint jp) {
        MethodSignature sig = (MethodSignature) jp.getSignature();
        Method method = sig.getMethod();
        RequireActiveSpace ann = method.getAnnotation(RequireActiveSpace.class);
        return (ann != null) ? ann : method.getDeclaringClass().getAnnotation(RequireActiveSpace.class);
    }

    private UUID resolveSpaceId(JoinPoint jp, String[] keys) {
        UUID fromPath = resolveFromPathVariables(jp, keys);
        return (fromPath != null) ? fromPath : resolveFromArgs(jp.getArgs());
    }

    private UUID resolveFromPathVariables(JoinPoint jp, String[] keys) {
        MethodSignature sig = (MethodSignature) jp.getSignature();
        Annotation[][] paramsAnns = sig.getMethod().getParameterAnnotations();
        Object[] args = jp.getArgs();

        for (int i = 0; i < paramsAnns.length; i++) {
            UUID u = uuidFromPathVar(paramsAnns[i], args[i], keys);
            if (u != null) return u;
        }
        return null;
    }

    private UUID uuidFromPathVar(Annotation[] paramAnns, Object arg, String[] keys) {
        for (Annotation a : paramAnns) {
            if (a instanceof PathVariable pv) {
                String name = pathVarName(pv);
                if (isKeyMatch(name, keys)) return tryUuid(arg);
            }
        }
        return null;
    }

    private String pathVarName(PathVariable pv) {
        if (pv == null) {
            throw new IllegalArgumentException("PathVariable annotation object cannot be null.");
        }

        return Stream.of(pv.name(), pv.value())
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse("");
    }

    private boolean isKeyMatch(String name, String[] keys) {
        for (String k : keys) if (k.equals(name)) return true;
        return false;
    }

    private UUID resolveFromArgs(Object[] args) {
        for (Object arg : args) {
            UUID u = tryUuid(arg);
            if (u != null) return u;
        }
        return null;
    }

    private UUID tryUuid(Object v) {
        if (v instanceof UUID u) return u;
        if (v instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                log.debug("Invalid UUID string: {}", s, e);
                return null;
            }
        }
        return null;
    }
}
