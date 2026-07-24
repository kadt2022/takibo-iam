package com.takibo.managementservice.domain.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceCodePolicyTest {

    private final SpaceCodePolicy policy = new SpaceCodePolicy();

    @Test
    void selects_an_explicit_non_blank_code_before_the_name() {
        assertThat(policy.resolveBaseCode("Finance", "Ignored"))
                .isEqualTo("Finance");
    }

    @Test
    void falls_back_to_the_name_for_a_missing_or_blank_code() {
        assertThat(policy.resolveBaseCode(null, "Finance"))
                .isEqualTo("Finance");
        assertThat(policy.resolveBaseCode("   ", "Finance"))
                .isEqualTo("Finance");
    }

    @Test
    void normalizes_and_bounds_a_candidate_to_thirty_characters() {
        assertThat(policy.normalizeCandidateCode(
                "Identity Core Finance Security Operations",
                1234
        )).isEqualTo("identity-core-finance-security");
    }

    @Test
    void uses_the_suffix_when_normalization_produces_a_short_code() {
        assertThat(policy.normalizeCandidateCode("!!!", 1234))
                .isEqualTo("space-1234");
    }
}
