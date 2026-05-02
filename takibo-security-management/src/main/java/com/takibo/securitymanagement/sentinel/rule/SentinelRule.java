package com.takibo.securitymanagement.sentinel.rule;

import com.takibo.securitymanagement.sentinel.advice.SentinelResponse;

@FunctionalInterface
public interface SentinelRule<T extends Throwable> {
    SentinelResponse toResponse(T ex, String path, String traceId);
}

