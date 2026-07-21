package com.takibo.securitymanagement.sentinel.advice;

import com.takibo.securitymanagement.sentinel.rule.SentinelRule;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Optional;
import java.util.UUID;

@ControllerAdvice
public class SentinelAdvice {

    private static final Logger log = LoggerFactory.getLogger(SentinelAdvice.class);
    private final SentinelRuleRegistry registry;

    public SentinelAdvice(SentinelRuleRegistry registry) {
        this.registry = registry;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SentinelResponse> handle(Exception ex, HttpServletRequest req) {
        Throwable cause = resolveHandledCause(ex);
        String path = req.getRequestURI();
        String traceId = Optional.ofNullable(MDC.get("traceId")).orElse(UUID.randomUUID().toString());

        SentinelRule<Throwable> sentinelRule = registry.resolve(cause);
        SentinelResponse resp = sentinelRule.toResponse(cause, path, traceId);

        log.warn("Exception intercepted [{}] traceId={} path={} -> code={} status={} message={}",
                cause.getClass().getName(), traceId, path, resp.code(), resp.status(), resp.message(), cause);

        return ResponseEntity.status(resp.status()).body(resp);
    }

    private Throwable resolveHandledCause(Exception failure) {
        Throwable current = failure;
        while (current != null) {
            if (registry.hasRule(current)) {
                return current;
            }
            current = current.getCause();
        }
        return Optional.ofNullable(NestedExceptionUtils.getRootCause(failure)).orElse(failure);
    }
}
