package com.takibo.securitymanagement.sentinel.advice;

import com.takibo.securitymanagement.sentinel.rule.SentinelRule;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ControllerAdvice
public class SentinelAdvice {

    private static final Logger log = LoggerFactory.getLogger(SentinelAdvice.class);
    private static final String CLIENT_ALREADY_EXISTS_EXCEPTION =
            "com.takibo.managementservice.domain.exception.ClientAlreadyExistsException";

    private final SentinelRuleRegistry registry;

    public SentinelAdvice(SentinelRuleRegistry registry) {
        this.registry = registry;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SentinelResponse> handle(Exception ex, HttpServletRequest req) {
        Throwable cause = Optional.ofNullable(NestedExceptionUtils.getRootCause(ex)).orElse(ex);
        String path = req.getRequestURI();
        String traceId = Optional.ofNullable(MDC.get("traceId")).orElse(UUID.randomUUID().toString());

        if (isClientAlreadyExists(cause)) {
            SentinelResponse response = new SentinelResponse(
                    Instant.now(),
                    HttpStatus.CONFLICT.value(),
                    SentinelErrorCode.OAUTH_CLIENT_ALREADY_EXISTS.name(),
                    resolveClientAlreadyExistsMessage(cause),
                    path,
                    traceId
            );
            log.warn("Exception intercepted [{}] traceId={} path={} -> code={} status={} message={}",
                    cause.getClass().getName(), traceId, path, response.code(), response.status(), response.message(), cause);
            return ResponseEntity.status(response.status()).body(response);
        }

        SentinelRule<Throwable> sentinelRule = registry.resolve(cause);
        SentinelResponse resp = sentinelRule.toResponse(cause, path, traceId);

        log.warn("Exception intercepted [{}] traceId={} path={} -> code={} status={} message={}",
                cause.getClass().getName(), traceId, path, resp.code(), resp.status(), resp.message(), cause);

        return ResponseEntity.status(resp.status()).body(resp);
    }

    private static boolean isClientAlreadyExists(Throwable cause) {
        return cause != null && CLIENT_ALREADY_EXISTS_EXCEPTION.equals(cause.getClass().getName());
    }

    private static String resolveClientAlreadyExistsMessage(Throwable cause) {
        String message = cause.getMessage();
        return (message == null || message.isBlank())
                ? "Client already exists"
                : message;
    }
}
