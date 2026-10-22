package com.takibo.adp.test.adapter;

import com.takibo.adp.api.BehaviorProfileView;
import com.takibo.adp.core.port.BehaviorProfileReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class TestBehaviorProfileReader implements BehaviorProfileReader {
    
    private static final long HOUR = 3600;
    private static final long DAY = 86400;
    
    @Override
    public Optional<BehaviorProfileView> findBySubjectId(String subjectId) {
        log.debug("Loading test profile for subject: {}", subjectId);
        
        if ("user".equals(subjectId)) {
            return Optional.of(createNormalUserProfile(subjectId));
        } else if ("admin".equals(subjectId)) {
            return Optional.of(createAdminProfile(subjectId));
        } else if ("suspicious".equals(subjectId)) {
            return Optional.of(createSuspiciousProfile(subjectId));
        }
        
        return Optional.empty();
    }
    
    private BehaviorProfileView createNormalUserProfile(String subjectId) {
        return profile(subjectId,
            "7a3f8b9c", 50, DAY * 30, HOUR,
            new BehaviorProfileView.VelocityStats(5.0, 2.0, 100),
            "CA", "Montreal", 40, HOUR);
    }
    
    private BehaviorProfileView createAdminProfile(String subjectId) {
        return profile(subjectId,
            "admin123", 100, DAY * 90, 600,
            new BehaviorProfileView.VelocityStats(3.0, 1.5, 200),
            "CA", "Montreal", 80, 600);
    }
    
    private BehaviorProfileView createSuspiciousProfile(String subjectId) {
        return profile(subjectId,
            "suspicious1", 2, DAY, DAY,
            new BehaviorProfileView.VelocityStats(2.0, 0.5, 5),
            "RU", "Moscow", 1, DAY);
    }
    
    private static BehaviorProfileView profile(
        String subjectId,
        String fingerprint,
        int fingerprintSeenCount,
        long fingerprintFirstSeenSecondsAgo,
        long fingerprintLastSeenSecondsAgo,
        BehaviorProfileView.VelocityStats velocity,
        String country,
        String city,
        int locationSeenCount,
        long locationLastSeenSecondsAgo
    ) {
        Instant now = Instant.now();
        
        return new BehaviorProfileView(
            subjectId,
            Map.of(fingerprint, new BehaviorProfileView.FingerprintStats(
                fingerprintSeenCount,
                now.minusSeconds(fingerprintFirstSeenSecondsAgo),
                now.minusSeconds(fingerprintLastSeenSecondsAgo)
            )),
            velocity,
            Map.of(country + ":" + city, new BehaviorProfileView.LocationStats(
                country,
                city,
                locationSeenCount,
                now.minusSeconds(locationLastSeenSecondsAgo)
            ))
        );
    }
}
