package com.takibo.audit.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    public static final String TRACE_ID_HEADER = "X-KRYPTION-ID";
    public static final String MDC_KEY = "traceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = request.getHeader(TRACE_ID_HEADER);

        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, traceId);
        MDC.put("clientIp", request.getRemoteAddr());
        MDC.put("userAgent", request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "unknown");
        MDC.put("clientApp", request.getHeader("X-Client-Id") != null ? request.getHeader("X-Client-Id") : "unknown");

        // Ajouter explicitement dans les headers de réponse
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader("X-Client-Ip", MDC.get("clientIp"));
        response.setHeader("X-Client-App", MDC.get("clientApp"));
        response.setHeader("X-User-Agent", MDC.get("userAgent"));

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.clear();
    }
}
