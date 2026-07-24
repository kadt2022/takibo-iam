package com.takibo.managementservice.application.service;

import com.takibo.managementservice.domain.policy.SpaceCodePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceCodeGeneratorTest {

    private final SpaceCodeGenerator generator =
            new SpaceCodeGenerator(new SpaceCodePolicy());

    @Test
    void given_requested_code_when_normalize_or_generate_then_uses_requested_code() {
        assertThat(generator.generateInitialCode("BUSA Finance", "Ignored Name"))
                .isEqualTo("busa-finance");
    }

    @Test
    void given_missing_requested_code_when_normalize_or_generate_then_uses_name() {
        assertThat(generator.generateInitialCode(null, "Takibo IAM"))
                .isEqualTo("takibo-iam");
    }

    @Test
    void given_long_requested_code_when_normalize_or_generate_then_returns_lowercase_kebab_case_limited_to_thirty_characters() {
        String code = generator.generateInitialCode(
                "Identity Core Finance Security Operations",
                "Ignored");

        assertThat(code).isEqualTo("identity-core-finance-security");
        assertThat(code).hasSizeLessThanOrEqualTo(30);
    }

    @Test
    void given_blank_inputs_when_normalize_or_generate_then_returns_non_empty_code_without_edge_hyphens() {
        String code = generator.generateInitialCode("", "!!!");

        assertThat(code).isNotBlank();
        assertThat(code).startsWith("space-");
        assertThat(code).doesNotStartWith("-");
        assertThat(code).doesNotEndWith("-");
    }
}
