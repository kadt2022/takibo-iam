package com.takibo.securitymanagement.sentinel.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.securitymanagement.sentinel.advice.SentinelResponse;
import com.takibo.securitymanagement.sentinel.rule.SentinelRule;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public class SentinelHttpErrorWriter {

    private final SentinelRuleRegistry sentinelRuleRegistry;
    private final ObjectMapper objectMapper;

    public SentinelHttpErrorWriter(SentinelRuleRegistry sentinelRuleRegistry, ObjectMapper objectMapper) {
        this.sentinelRuleRegistry = sentinelRuleRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Transforme une exception en SentinelResponse (via RuleRegistry),
     * puis l'écrit dans la réponse HTTP.
     */
    @SuppressWarnings("unchecked")
    public void write(Throwable ex,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {

        String path = request.getRequestURI();
        String traceId = Optional.ofNullable(MDC.get("traceId"))
                .filter(s -> !s.isBlank())
                .orElse(UUID.randomUUID().toString());

        SentinelRule<Throwable> sentinelRule = sentinelRuleRegistry.resolve(ex);
        SentinelResponse body = sentinelRule.toResponse(ex, path, traceId);

        response.setStatus(body.status());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getWriter(), body);
    }
}
