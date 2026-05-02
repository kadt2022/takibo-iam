package com.takibo.audit.domain;

import lombok.*;
import lombok.Builder;
import lombok.Data;
import org.springframework.boot.logging.LogLevel;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEvent {
    private Instant timestamp;
    private String action;
    private Object params;
    private LogLevel level;
    private String userId;
    private String actorAccountId;
    private String actorUserId;
    private String orgId;
    private String spaceId;
    private String actorType;
    private String actorSource;
    private String method;
    private String status;
    private String error;
    private String traceId;
    private String clientIp;
    private String userAgent;
    private String clientApp;

}
