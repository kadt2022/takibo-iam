package com.takibo.managementservice.application.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TakiboCodeNormalizerTest {

    @Test
    void given_human_labels_when_normalized_then_returns_lowercase_kebab_case() {
        assertThat(TakiboCodeNormalizer.normalize("Takibo IAM")).isEqualTo("takibo-iam");
        assertThat(TakiboCodeNormalizer.normalize("BUSA Finance")).isEqualTo("busa-finance");
        assertThat(TakiboCodeNormalizer.normalize("\u00c9quipe S\u00e9curit\u00e9")).isEqualTo("equipe-securite");
        assertThat(TakiboCodeNormalizer.normalize(" identity_core ")).isEqualTo("identity-core");
    }

    @Test
    void given_short_organization_code_when_normalized_then_throws_exception() {
        assertThatThrownBy(() -> TakiboCodeNormalizer.normalizeOrg("ab"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Organization code");
    }

    @Test
    void given_short_space_code_when_normalized_then_appends_suffix() {
        assertThat(TakiboCodeNormalizer.normalizeSpace("ab", 1234)).isEqualTo("ab-1234");
    }

    @Test
    void given_blank_space_code_when_normalized_then_uses_space_prefix() {
        assertThat(TakiboCodeNormalizer.normalizeSpace("   ", 1234)).isEqualTo("space-1234");
    }
}
