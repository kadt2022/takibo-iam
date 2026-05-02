package com.takibo.securitymanagement.domain.model;

import java.time.Instant;

public record Environment(
        Instant time,
        String ipAddress,
        int riskScore
) {}
