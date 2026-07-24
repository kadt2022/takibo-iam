package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.model.SpaceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceStatusTransitionPolicyTest {

    private final SpaceStatusTransitionPolicy policy =
            new SpaceStatusTransitionPolicy();

    @Test
    void returns_no_transition_for_an_idempotent_status_request() {
        assertThat(policy.resolveTransition(
                SpaceStatus.ACTIVE,
                SpaceStatus.ACTIVE
        )).isEmpty();
    }

    @Test
    void returns_the_requested_status_when_it_changes() {
        assertThat(policy.resolveTransition(
                SpaceStatus.ACTIVE,
                SpaceStatus.SUSPENDED
        )).hasValueSatisfying(transition -> {
            assertThat(transition.current()).isEqualTo(SpaceStatus.ACTIVE);
            assertThat(transition.requested())
                    .isEqualTo(SpaceStatus.SUSPENDED);
        });
    }
}
