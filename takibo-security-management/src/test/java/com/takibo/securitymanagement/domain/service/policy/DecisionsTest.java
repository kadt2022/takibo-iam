package com.takibo.securitymanagement.domain.service.policy;

import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static com.takibo.securitymanagement.domain.service.policy.Decisions.Grant.grant;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.Requirement.require;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.firstGrant;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.firstUnmet;
import static org.assertj.core.api.Assertions.assertThat;

class DecisionsTest {

    /** Condition qui ne doit jamais être évaluée : l'évaluation s'arrête avant elle. */
    private static final BooleanSupplier NEVER_EVALUATED = () -> {
        throw new AssertionError("condition evaluated after the decision was made");
    };

    @Test
    void firstUnmet_deniesWithTheFirstUnmetRequirement_andStopsThere() {
        assertThat(firstUnmet(
                require(() -> true, "POL_A", "a"),
                require(() -> false, "POL_B", "b"),
                require(NEVER_EVALUATED, "POL_C", "c")))
                .hasValueSatisfying(decision -> {
                    assertThat(decision.isDeny()).isTrue();
                    assertThat(decision.getPolicyId()).isEqualTo("POL_B");
                    assertThat(decision.getReason()).isEqualTo("b");
                });
    }

    @Test
    void firstUnmet_isEmptyWhenEveryRequirementHolds() {
        assertThat(firstUnmet(require(() -> true, "POL_A", "a"), require(() -> true, "POL_B", "b"))).isEmpty();
    }

    @Test
    void firstGrant_permitsWithTheFirstApplicableReason_andStopsThere() {
        assertThat(firstGrant("POL_READ",
                grant(() -> false, "first"),
                grant(() -> true, "second"),
                grant(NEVER_EVALUATED, "third")))
                .hasValueSatisfying(decision -> {
                    assertThat(decision.isPermit()).isTrue();
                    assertThat(decision.getPolicyId()).isEqualTo("POL_READ");
                    assertThat(decision.getReason()).isEqualTo("second");
                });
        assertThat(firstGrant("POL_READ", grant(() -> false, "none"))).isEmpty();
    }
}
