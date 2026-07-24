package com.takibo.managementservice.domain.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationCodeNormalizerTest {

    private final OrganizationCodeNormalizer normalizer =
            new OrganizationCodeNormalizer();

    @Test
    void normalizes_an_organization_boundary_code() {
        assertThat(normalizer.normalize("\u00c9quipe Takibo"))
                .isEqualTo("equipe-takibo");
    }

    @Test
    void rejects_a_code_shorter_than_the_boundary_minimum() {
        assertThatThrownBy(() -> normalizer.normalize("ab"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Organization code must contain at least three "
                                + "characters after normalization"
                );
    }
}
