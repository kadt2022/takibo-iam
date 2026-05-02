package com.takibo.adp.api;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record DecisionRequest(
    String subjectId,
    String organizationId,
    String spaceId,
    Set<String> roles,
    Set<String> permissions,
    
    String resourcePath,
    String httpMethod,
    
    Instant timestamp,
    String ipAddress,
    String deviceFingerprint,
    String userAgent,
    
    String country,
    String city,
    Boolean isVpn,
    Boolean isProxy,
    Boolean isTor,
    
    String sessionId,
    Integer requestCountLast10s,
    Integer requestCountLast60s,
    
    int timeoutMs,
    String policyVersion,
    
    Map<String, String> metadata
) {
    public DecisionRequest {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId is required");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp is required");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
        if (roles == null) {
            roles = Set.of();
        }
        if (permissions == null) {
            permissions = Set.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
