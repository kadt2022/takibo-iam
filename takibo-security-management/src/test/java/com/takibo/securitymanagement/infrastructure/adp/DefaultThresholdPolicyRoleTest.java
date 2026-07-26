package com.takibo.securitymanagement.infrastructure.adp;

import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.api.Thresholds;
import com.takibo.adp.spring.adapter.DefaultThresholdPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultThresholdPolicyRoleTest {

    private final DefaultThresholdPolicy policy = new DefaultThresholdPolicy();

    @Test
    void onlyCanonicalPlatformRoleTriggersAdminAdjustment() {
        Thresholds canonical = policy.calculate(request(Set.of("R_TAKIBO_PLATFORM_ADMIN")));
        assertThat(canonical.reason()).contains("admin role");

        for (String ghost : new String[]{
                "PLATFORM_ADMIN", "R_PLATFORM_ADMIN", "ROLE_PLATFORM_ADMIN"}) {
            assertThat(policy.calculate(request(Set.of(ghost))).reason())
                    .as(ghost)
                    .doesNotContain("admin role");
        }
    }

    private DecisionRequest request(Set<String> roles) {
        return new DecisionRequest(
                "subject", null, null, roles, Set.of(),
                null, "GET",
                Instant.parse("2026-07-22T15:00:00Z"),
                null, null, null,
                null, null, null, null, null,
                null, null, null,
                1_000, "test", Map.of());
    }
}
