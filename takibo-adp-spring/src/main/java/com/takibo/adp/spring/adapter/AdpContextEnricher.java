package com.takibo.adp.spring.adapter;

import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.spring.config.AdpProperties;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class AdpContextEnricher {
    
    private final AdpProperties properties;
    private final RequestVelocityTracker velocityTracker;
    
    public DecisionRequest buildRequest(
        TakiboSecurityContext securityContext,
        HttpServletRequest httpRequest,
        Set<String> roles,
        Set<String> permissions
    ) {
        String subjectId = securityContext.subject() != null 
            ? securityContext.subject().subjectId() 
            : null;
        
        String organizationId = securityContext.tenant() != null 
            ? securityContext.tenant().organizationId() 
            : null;
        
        String spaceId = securityContext.tenant() != null 
            ? securityContext.tenant().spaceId() 
            : null;
        
        String ipAddress = securityContext.transport() != null 
            ? securityContext.transport().ipAddress() 
            : resolveClientIp(httpRequest);
        
        String deviceFingerprint = extractDeviceFingerprint(httpRequest);
        
        RequestVelocityTracker.VelocitySnapshot velocity = 
            velocityTracker.getVelocity(subjectId);
        
        Boolean isProxy = detectProxy(httpRequest);
        Boolean isVpn = null;
        Boolean isTor = null;
        
        return new DecisionRequest(
            subjectId,
            organizationId,
            spaceId,
            roles != null ? roles : Set.of(),
            permissions != null ? permissions : Set.of(),
            httpRequest.getRequestURI(),
            httpRequest.getMethod(),
            Instant.now(),
            ipAddress,
            deviceFingerprint,
            httpRequest.getHeader("User-Agent"),
            null,
            null,
            isVpn,
            isProxy,
            isTor,
            httpRequest.getSession(false) != null 
                ? httpRequest.getSession(false).getId() 
                : null,
            velocity.last10s(),
            velocity.last60s(),
            properties.getTimeoutMs(),
            properties.getPolicyVersion(),
            Map.of()
        );
    }
    
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            return comma > 0 ? forwardedFor.substring(0, comma).trim() : forwardedFor.trim();
        }
        
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        
        return request.getRemoteAddr();
    }
    
    private String extractDeviceFingerprint(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        String accept = request.getHeader("Accept");
        String lang = request.getHeader("Accept-Language");
        
        if (ua == null && accept == null) {
            return "UNKNOWN";
        }
        
        StringBuilder sb = new StringBuilder();
        if (ua != null) sb.append(ua);
        sb.append("|");
        if (accept != null) sb.append(accept);
        sb.append("|");
        if (lang != null) sb.append(lang);
        
        return Integer.toHexString(sb.toString().hashCode());
    }
    
    private Boolean detectProxy(HttpServletRequest request) {
        String via = request.getHeader("Via");
        String forwarded = request.getHeader("X-Forwarded-For");
        return via != null || (forwarded != null && forwarded.split(",").length > 1);
    }
}
