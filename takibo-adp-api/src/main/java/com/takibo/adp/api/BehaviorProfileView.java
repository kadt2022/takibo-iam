package com.takibo.adp.api;

import java.time.Instant;
import java.util.Map;

public record BehaviorProfileView(
    String subjectId,
    Map<String, FingerprintStats> fingerprints,
    VelocityStats velocity,
    Map<String, LocationStats> locations
) {
    public record FingerprintStats(
        int seenCount,
        Instant firstSeen,
        Instant lastSeen
    ) {}

    public record VelocityStats(
        double avgPerMinute,
        double stdDev,
        long sampleCount
    ) {}
    
    public record LocationStats(
        String country,
        String city,
        int seenCount,
        Instant lastSeen
    ) {}
}
