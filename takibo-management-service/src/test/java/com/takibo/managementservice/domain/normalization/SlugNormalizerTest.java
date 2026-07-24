package com.takibo.managementservice.domain.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugNormalizerTest {

    @Test
    void removes_leading_and_trailing_hyphens_after_normalization() {
        assertThat(SlugNormalizer.normalize(" -- Takibo IAM -- "))
                .isEqualTo("takibo-iam");
    }

    @Test
    void normalizes_a_null_value_as_an_empty_slug() {
        assertThat(SlugNormalizer.normalize(null)).isEmpty();
    }
}
