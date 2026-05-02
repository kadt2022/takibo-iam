package com.takibo.adp.api;

public record Thresholds(
    double denyThreshold,
    double challengeThreshold,
    String reason
) {}
