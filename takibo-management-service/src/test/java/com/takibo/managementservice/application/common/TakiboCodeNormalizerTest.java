package com.takibo.managementservice.application.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TakiboCodeNormalizerTest {

    @Test
    void normalizes_human_labels_to_lowercase_kebab_case() {
        assertThat(TakiboCodeNormalizer.normalize("Takibo IAM")).isEqualTo("takibo-iam");
        assertThat(TakiboCodeNormalizer.normalize("BUSA Finance")).isEqualTo("busa-finance");
        assertThat(TakiboCodeNormalizer.normalize("\u00c9quipe S\u00e9curit\u00e9")).isEqualTo("equipe-securite");
        assertThat(TakiboCodeNormalizer.normalize(" identity_core ")).isEqualTo("identity-core");
    }

    @Test
    void rejects_organization_code_that_is_too_short_after_normalization() {
        assertThatThrownBy(() -> TakiboCodeNormalizer.normalizeOrg("ab"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Organization code");
    }

    @Test
    void appends_suffix_to_short_space_code() {
        assertThat(TakiboCodeNormalizer.normalizeSpace("ab", 1234)).isEqualTo("ab-1234");
    }

    @Test
    void uses_space_prefix_when_space_code_normalizes_to_empty() {
        assertThat(TakiboCodeNormalizer.normalizeSpace("   ", 1234)).isEqualTo("space-1234");
    }
}
