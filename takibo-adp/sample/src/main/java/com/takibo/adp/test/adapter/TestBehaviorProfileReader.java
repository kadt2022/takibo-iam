package com.takibo.adp.test.adapter;

import com.takibo.adp.api.BehaviorProfileView;
import com.takibo.adp.core.port.BehaviorProfileReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class TestBehaviorProfileReader implements BehaviorProfileReader {
    
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
        Map<String, BehaviorProfileView.FingerprintStats> fingerprints = new HashMap<>();
        
        String commonFingerprint = "7a3f8b9c";
        fingerprints.put(commonFingerprint, new BehaviorProfileView.FingerprintStats(
            50,
            Instant.now().minusSeconds(86400 * 30),
            Instant.now().minusSeconds(3600)
        ));
        
        BehaviorProfileView.VelocityStats velocity = new BehaviorProfileView.VelocityStats(
            5.0,
            2.0,
            100
        );
        
        Map<String, BehaviorProfileView.LocationStats> locations = new HashMap<>();
        locations.put("CA:Montreal", new BehaviorProfileView.LocationStats(
            "CA",
            "Montreal",
            40,
            Instant.now().minusSeconds(3600)
        ));
        
        return new BehaviorProfileView(
            subjectId,
            fingerprints,
            velocity,
            locations
        );
    }
    
    private BehaviorProfileView createAdminProfile(String subjectId) {
        Map<String, BehaviorProfileView.FingerprintStats> fingerprints = new HashMap<>();
        
        String adminFingerprint = "admin123";
        fingerprints.put(adminFingerprint, new BehaviorProfileView.FingerprintStats(
            100,
            Instant.now().minusSeconds(86400 * 90),
            Instant.now().minusSeconds(600)
        ));
        
        BehaviorProfileView.VelocityStats velocity = new BehaviorProfileView.VelocityStats(
            3.0,
            1.5,
            200
        );
        
        Map<String, BehaviorProfileView.LocationStats> locations = new HashMap<>();
        locations.put("CA:Montreal", new BehaviorProfileView.LocationStats(
            "CA",
            "Montreal",
            80,
            Instant.now().minusSeconds(600)
        ));
        
        return new BehaviorProfileView(
            subjectId,
            fingerprints,
            velocity,
            locations
        );
    }
    
    private BehaviorProfileView createSuspiciousProfile(String subjectId) {
        Map<String, BehaviorProfileView.FingerprintStats> fingerprints = new HashMap<>();
        
        String suspiciousFingerprint = "suspicious1";
        fingerprints.put(suspiciousFingerprint, new BehaviorProfileView.FingerprintStats(
            2,
            Instant.now().minusSeconds(86400),
            Instant.now().minusSeconds(86400)
        ));
        
        BehaviorProfileView.VelocityStats velocity = new BehaviorProfileView.VelocityStats(
            2.0,
            0.5,
            5
        );
        
        Map<String, BehaviorProfileView.LocationStats> locations = new HashMap<>();
        locations.put("RU:Moscow", new BehaviorProfileView.LocationStats(
            "RU",
            "Moscow",
            1,
            Instant.now().minusSeconds(86400)
        ));
        
        return new BehaviorProfileView(
            subjectId,
            fingerprints,
            velocity,
            locations
        );
    }
}
