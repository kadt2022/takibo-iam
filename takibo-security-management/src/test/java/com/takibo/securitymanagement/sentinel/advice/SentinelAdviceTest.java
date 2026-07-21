package com.takibo.securitymanagement.sentinel.advice;

import com.takibo.securitymanagement.sentinel.rule.SentinelRuleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelAdviceTest {

    private static final String PATH = "/api/v1/orgs";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void handle_preservesFirstRegisteredExceptionInCauseChain() {
        SentinelRuleRegistry registry = registry();
        registry.register(ConcurrentCreationException.class,
                (ex, path, traceId) -> response(409, "CREATION_CONFLICT", ex.getMessage(), path, traceId));
        SentinelAdvice advice = new SentinelAdvice(registry);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
        MDC.put("traceId", "trace-409");
        Exception failure = new Exception("MVC wrapper",
                new ConcurrentCreationException("Organization already exists",
                        new IllegalStateException("database constraint")));

        var result = advice.handle(failure, request);

        assertThat(result.getStatusCode().value()).isEqualTo(409);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("CREATION_CONFLICT");
        assertThat(result.getBody().message()).isEqualTo("Organization already exists");
        assertThat(result.getBody().path()).isEqualTo(PATH);
        assertThat(result.getBody().traceId()).isEqualTo("trace-409");
    }

    @Test
    void handle_usesRootCauseWhenNoExceptionIsRegistered() {
        SentinelAdvice advice = new SentinelAdvice(registry());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
        Exception failure = new Exception("MVC wrapper", new IllegalArgumentException("database failure"));

        var result = advice.handle(failure, request);

        assertThat(result.getStatusCode().value()).isEqualTo(500);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().message()).isEqualTo("database failure");
        assertThat(result.getBody().traceId()).isNotBlank();
    }

    @Test
    void handle_usesOriginalExceptionWhenItHasNoCauseAndNoRule() {
        SentinelAdvice advice = new SentinelAdvice(registry());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);

        var result = advice.handle(new Exception("standalone failure"), request);

        assertThat(result.getStatusCode().value()).isEqualTo(500);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().message()).isEqualTo("standalone failure");
    }

    private static SentinelRuleRegistry registry() {
        return new SentinelRuleRegistry(
                (ex, path, traceId) -> response(500, "FALLBACK", ex.getMessage(), path, traceId));
    }

    private static SentinelResponse response(int status, String code, String message, String path, String traceId) {
        return new SentinelResponse(Instant.now(), status, code, message, path, traceId);
    }

    private static final class ConcurrentCreationException extends RuntimeException {
        private ConcurrentCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
