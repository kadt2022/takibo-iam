package com.takibo.securitymanagement.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PolicyDecision {

    private final Effect effect;
    private final String policyId;
    private final String reason;

    public boolean isPermit() {
        return effect == Effect.PERMIT;
    }

    public boolean isDeny() {
        return effect == Effect.DENY;
    }
}
