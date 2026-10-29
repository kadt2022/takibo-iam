package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Effect;
import com.takibo.securitymanagement.domain.model.PolicyDecision;

import java.util.Optional;

final class PolicyDecisions {

    static final String POL_ORG_MISMATCH = "POL_ORG_MISMATCH";

    private PolicyDecisions() {
    }

    static Optional<PolicyDecision> deny(String policyId, String reason) {
        return Optional.of(PolicyDecision.builder()
                .effect(Effect.DENY)
                .policyId(policyId)
                .reason(reason)
                .build());
    }

    static Optional<PolicyDecision> permit(String policyId, String reason) {
        return Optional.of(PolicyDecision.builder()
                .effect(Effect.PERMIT)
                .policyId(policyId)
                .reason(reason)
                .build());
    }
}
