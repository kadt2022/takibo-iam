package com.takibo.adp.spring.adapter;

import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.api.Thresholds;
import com.takibo.adp.core.port.ThresholdPolicy;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DefaultThresholdPolicy implements ThresholdPolicy {
    
    private static final double BASE_DENY_THRESHOLD = 75.0;
    private static final double BASE_CHALLENGE_THRESHOLD = 50.0;
    
    @Override
    public Thresholds calculate(DecisionRequest request) {
        double denyThreshold = BASE_DENY_THRESHOLD;
        double challengeThreshold = BASE_CHALLENGE_THRESHOLD;
        
        List<String> adjustments = new ArrayList<>();
        
        if (request.resourcePath() != null) {
            if (request.resourcePath().contains("/admin") || 
                request.resourcePath().contains("/api/platform")) {
                denyThreshold -= 20.0;
                challengeThreshold -= 15.0;
                adjustments.add("admin resource");
            } else if (request.resourcePath().contains("/public")) {
                denyThreshold += 10.0;
                challengeThreshold += 10.0;
                adjustments.add("public resource");
            }
        }
        
        if (request.roles() != null && request.roles().contains("PLATFORM_ADMIN")) {
            denyThreshold -= 10.0;
            adjustments.add("admin role");
        }
        
        if (request.timestamp() != null) {
            int hour = request.timestamp().atZone(java.time.ZoneId.systemDefault()).getHour();
            int dayOfWeek = request.timestamp().atZone(java.time.ZoneId.systemDefault()).getDayOfWeek().getValue();
            
            boolean businessHours = dayOfWeek >= 1 && dayOfWeek <= 5 && hour >= 8 && hour <= 18;
            
            if (!businessHours) {
                denyThreshold -= 15.0;
                challengeThreshold -= 10.0;
                adjustments.add("outside business hours");
            }
        }
        
        denyThreshold = Math.max(50.0, Math.min(95.0, denyThreshold));
        challengeThreshold = Math.max(25.0, Math.min(70.0, challengeThreshold));
        
        if (challengeThreshold >= denyThreshold) {
            challengeThreshold = denyThreshold - 15.0;
        }
        
        String reason = adjustments.isEmpty() 
            ? "baseline" 
            : String.join(", ", adjustments);
        
        log.debug("Threshold policy: deny={} challenge={} ({})", 
            denyThreshold, challengeThreshold, reason);
        
        return new Thresholds(denyThreshold, challengeThreshold, reason);
    }
}
