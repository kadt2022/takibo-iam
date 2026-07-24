package com.takibo.managementservice.domain.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantCodeNormalizerTest {

    @Test
    void normalizes_human_labels_as_lowercase_kebab_case() {
        assertThat(TenantCodeNormalizer.normalize("Takibo IAM"))
                .isEqualTo("takibo-iam");
        assertThat(TenantCodeNormalizer.normalize("BUSA Finance"))
                .isEqualTo("busa-finance");
        assertThat(TenantCodeNormalizer.normalize(
                "\u00c9quipe S\u00e9curit\u00e9"
        )).isEqualTo("equipe-securite");
        assertThat(TenantCodeNormalizer.normalize(" identity_core "))
                .isEqualTo("identity-core");
    }

    @Test
    void rejects_a_short_organization_code() {
        assertThatThrownBy(() ->
                TenantCodeNormalizer.normalizeOrganizationCode("ab")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Organization code");
    }

    @Test
    void appends_a_suffix_to_a_short_space_code() {
        assertThat(TenantCodeNormalizer.normalizeSpaceCode("ab", 1234))
                .isEqualTo("ab-1234");
    }

    @Test
    void uses_a_space_prefix_for_a_blank_space_code() {
        assertThat(TenantCodeNormalizer.normalizeSpaceCode("   ", 1234))
                .isEqualTo("space-1234");
    }
}
