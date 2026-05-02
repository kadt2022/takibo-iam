package com.takibo.audit.infrastructure.config;

import lombok.Builder;
import lombok.Data;
import org.slf4j.MDC;

@Builder
@Data
public class AuditContext {
    private static final ThreadLocal<AuditContext> currentContext = new ThreadLocal<>();

    private String traceId;
    private String clientIp;
    private String userAgent;
    private String clientApp;
    private Long startTime;

    public static AuditContext init() {
        AuditContext context = AuditContext.builder()
                .traceId(MDC.get("traceId"))
                .clientIp(MDC.get("clientIp"))
                .userAgent(MDC.get("userAgent"))
                .clientApp(MDC.get("clientApp"))
                .startTime(System.currentTimeMillis())
                .build();
        currentContext.set(context);
        return context;
    }

    public static AuditContext getCurrent() {
        return currentContext.get();
    }

    public static void clear() {
        currentContext.remove();
    }
}