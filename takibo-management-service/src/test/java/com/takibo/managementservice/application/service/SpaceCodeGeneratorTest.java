package com.takibo.managementservice.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceCodeGeneratorTest {

    private final SpaceCodeGenerator generator = new SpaceCodeGenerator();

    @Test
    void generates_from_requested_code_when_provided() {
        assertThat(generator.normalizeOrGenerate("BUSA Finance", "Ignored Name"))
                .isEqualTo("busa-finance");
    }

    @Test
    void generates_from_name_when_requested_code_is_absent() {
        assertThat(generator.normalizeOrGenerate(null, "Takibo IAM"))
                .isEqualTo("takibo-iam");
    }

    @Test
    void returns_lowercase_kebab_case_limited_to_thirty_characters() {
        String code = generator.normalizeOrGenerate(
                "Identity Core Finance Security Operations",
                "Ignored");

        assertThat(code).isEqualTo("identity-core-finance-security");
        assertThat(code).hasSizeLessThanOrEqualTo(30);
    }

    @Test
    void never_returns_empty_or_edge_hyphenated_code() {
        String code = generator.normalizeOrGenerate("", "!!!");

        assertThat(code).isNotBlank();
        assertThat(code).startsWith("space-");
        assertThat(code).doesNotStartWith("-");
        assertThat(code).doesNotEndWith("-");
    }
}
