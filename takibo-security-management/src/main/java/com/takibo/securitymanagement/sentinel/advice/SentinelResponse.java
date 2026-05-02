package com.takibo.securitymanagement.sentinel.advice;

import java.time.Instant;

public record SentinelResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId
) {}
