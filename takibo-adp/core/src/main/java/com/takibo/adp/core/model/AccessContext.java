package com.takibo.adp.core.model;

import com.takibo.adp.api.DecisionRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

@Getter
@Builder
public class AccessContext {
    
    private final String subjectId;
    private final String organizationId;
    private final String spaceId;
    private final Set<String> roles;
    private final Set<String> permissions;
    
    private final Instant timestamp;
    private final ZoneId userTimezone;
    
    private final String ipAddress;
    private final String country;
    private final String city;
    private final Boolean isVpn;
    private final Boolean isProxy;
    private final Boolean isTor;
    
    private final String deviceFingerprint;
    private final String userAgent;
    
    private final String resourcePath;
    private final String httpMethod;
    
    private final String sessionId;
    private final Integer requestCountLast10s;
    private final Integer requestCountLast60s;
    
    private final Map<String, String> metadata;
    
    public static AccessContext fromRequest(DecisionRequest request) {
        return AccessContext.builder()
            .subjectId(request.subjectId())
            .organizationId(request.organizationId())
            .spaceId(request.spaceId())
            .roles(request.roles())
            .permissions(request.permissions())
            .timestamp(request.timestamp())
            .userTimezone(ZoneId.systemDefault())
            .ipAddress(request.ipAddress())
            .country(request.country())
            .city(request.city())
            .isVpn(request.isVpn())
            .isProxy(request.isProxy())
            .isTor(request.isTor())
            .deviceFingerprint(request.deviceFingerprint())
            .userAgent(request.userAgent())
            .resourcePath(request.resourcePath())
            .httpMethod(request.httpMethod())
            .sessionId(request.sessionId())
            .requestCountLast10s(request.requestCountLast10s())
            .requestCountLast60s(request.requestCountLast60s())
            .metadata(request.metadata())
            .build();
    }
    
    public boolean isHighRiskNetwork() {
        return Boolean.TRUE.equals(isVpn) ||
               Boolean.TRUE.equals(isProxy) ||
               Boolean.TRUE.equals(isTor);
    }
    
    public boolean isDuringBusinessHours() {
        if (timestamp == null) {
            return true;
        }
        int hour = timestamp.atZone(userTimezone).getHour();
        int dayOfWeek = timestamp.atZone(userTimezone).getDayOfWeek().getValue();
        return dayOfWeek >= 1 && dayOfWeek <= 5 && hour >= 8 && hour <= 18;
    }
}
