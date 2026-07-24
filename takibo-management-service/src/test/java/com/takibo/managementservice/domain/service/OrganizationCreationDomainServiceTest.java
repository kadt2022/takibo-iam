package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.model.OrganizationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationCreationDomainServiceTest {

    private final OrganizationCreationDomainService service =
            new OrganizationCreationDomainService();

    @Test
    void prepares_a_normalized_active_organization() {
        var plan = service.prepareCreation("Takibo IAM", "Takibo");

        assertThat(plan.code()).isEqualTo("takibo-iam");
        assertThat(plan.name()).isEqualTo("Takibo");
        assertThat(plan.status()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    void rejects_an_organization_code_that_is_too_short_after_normalization() {
        assertThatThrownBy(() -> service.prepareCreation("ab", "Takibo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Organization code must contain at least three "
                                + "characters after normalization"
                );
    }
}
