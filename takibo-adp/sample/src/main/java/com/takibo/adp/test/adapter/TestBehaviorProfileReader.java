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
        Instant now = Instant.now();
        return profile(subjectId, "7a3f8b9c",
            new BehaviorProfileView.FingerprintStats(50, now.minusSeconds(DAY * 30), now.minusSeconds(HOUR)),
            new BehaviorProfileView.VelocityStats(5.0, 2.0, 100),
            new BehaviorProfileView.LocationStats("CA", "Montreal", 40, now.minusSeconds(HOUR)));
    }
    
    private BehaviorProfileView createAdminProfile(String subjectId) {
        Instant now = Instant.now();
        return profile(subjectId, "admin123",
            new BehaviorProfileView.FingerprintStats(100, now.minusSeconds(DAY * 90), now.minusSeconds(600)),
            new BehaviorProfileView.VelocityStats(3.0, 1.5, 200),
            new BehaviorProfileView.LocationStats("CA", "Montreal", 80, now.minusSeconds(600)));
    }
    
    private BehaviorProfileView createSuspiciousProfile(String subjectId) {
        Instant now = Instant.now();
        return profile(subjectId, "suspicious1",
            new BehaviorProfileView.FingerprintStats(2, now.minusSeconds(DAY), now.minusSeconds(DAY)),
            new BehaviorProfileView.VelocityStats(2.0, 0.5, 5),
            new BehaviorProfileView.LocationStats("RU", "Moscow", 1, now.minusSeconds(DAY)));
    }
    
    private static BehaviorProfileView profile(
        String subjectId,
        String fingerprint,
        BehaviorProfileView.FingerprintStats fingerprintStats,
        BehaviorProfileView.VelocityStats velocity,
        BehaviorProfileView.LocationStats location
    ) {
        return new BehaviorProfileView(
            subjectId,
            Map.of(fingerprint, fingerprintStats),
            velocity,
            Map.of(location.country() + ":" + location.city(), location)
        );
    }
}
