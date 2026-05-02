package com.takibo.adp.core.evaluator.impl;

import com.takibo.adp.api.BehaviorProfileView;
import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.*;
import com.takibo.adp.core.model.AccessContext;
import com.takibo.adp.core.port.BehaviorProfileReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class DeviceBaselineEvaluator implements ContextEvaluator {
    
    private static final int FREQUENCY_SATURATION = 10;
    
    private final BehaviorProfileReader profileReader;
    private final boolean enabled;
    
    @Override
    public String getName() {
        return "DeviceBaselineEvaluator";
    }
    
    @Override
    public double getWeight() {
        return 0.25;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public EvaluatorResult evaluate(AccessContext context) {
        String fingerprint = context.getDeviceFingerprint();
        
        if (fingerprint == null || fingerprint.isBlank() || "UNKNOWN".equals(fingerprint)) {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(60.0)
                .confidence(0.5)
                .reason("No device fingerprint available")
                .recommendation(Recommendation.CHALLENGE)
                .status(EvaluatorStatus.NO_DATA)
                .build();
        }
        
        Optional<BehaviorProfileView> profileOpt = profileReader.findBySubjectId(context.getSubjectId());
        
        if (profileOpt.isEmpty()) {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(50.0)
                .confidence(0.3)
                .reason("No behavioral profile available")
                .recommendation(Recommendation.CHALLENGE)
                .status(EvaluatorStatus.NO_DATA)
                .build();
        }
        
        BehaviorProfileView profile = profileOpt.get();
        Map<String, BehaviorProfileView.FingerprintStats> fingerprints = profile.fingerprints();
        
        if (fingerprints == null || fingerprints.isEmpty()) {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(55.0)
                .confidence(0.4)
                .reason("No fingerprint history")
                .recommendation(Recommendation.CHALLENGE)
                .status(EvaluatorStatus.NO_DATA)
                .build();
        }
        
        BehaviorProfileView.FingerprintStats stats = fingerprints.get(fingerprint);
        
        if (stats == null) {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(75.0)
                .confidence(0.8)
                .reason("New device fingerprint detected")
                .recommendation(Recommendation.CHALLENGE)
                .status(EvaluatorStatus.OK)
                .build();
        }
        
        Instant now = context.getTimestamp();
        double trustScore = calculateTrustScore(stats, now);
        double riskScore = 100.0 * (1.0 - trustScore);
        
        String reason = String.format("Known device (seen %dx, last %s)", 
            stats.seenCount(),
            formatDuration(Duration.between(stats.lastSeen(), now)));
        
        Recommendation recommendation = riskScore > 60.0 
            ? Recommendation.CHALLENGE 
            : Recommendation.ALLOW;
        
        return EvaluatorResult.builder()
            .evaluatorName(getName())
            .riskScore(riskScore)
            .confidence(0.85)
            .reason(reason)
            .recommendation(recommendation)
            .status(EvaluatorStatus.OK)
            .build();
    }
    
    private double calculateTrustScore(BehaviorProfileView.FingerprintStats stats, Instant now) {
        double recencyScore = calculateRecencyScore(stats.lastSeen(), now);
        double frequencyScore = calculateFrequencyScore(stats.seenCount());
        double ageScore = calculateAgeScore(stats.firstSeen(), now);
        
        return 0.4 * recencyScore + 0.3 * frequencyScore + 0.3 * ageScore;
    }
    
    private double calculateRecencyScore(Instant lastSeen, Instant now) {
        long daysSince = Duration.between(lastSeen, now).toDays();
        
        if (daysSince <= 7) return 1.0;
        if (daysSince <= 30) return 0.8;
        if (daysSince <= 90) return 0.5;
        return 0.2;
    }
    
    private double calculateFrequencyScore(int seenCount) {
        return 1.0 - Math.exp(-seenCount / (double) FREQUENCY_SATURATION);
    }
    
    private double calculateAgeScore(Instant firstSeen, Instant now) {
        long daysSinceFirst = Duration.between(firstSeen, now).toDays();
        
        if (daysSinceFirst >= 90) return 1.0;
        if (daysSinceFirst >= 30) return 0.8;
        if (daysSinceFirst >= 7) return 0.5;
        return 0.3;
    }
    
    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        if (days > 0) return days + "d ago";
        
        long hours = duration.toHours();
        if (hours > 0) return hours + "h ago";
        
        long minutes = duration.toMinutes();
        return minutes + "m ago";
    }
}
