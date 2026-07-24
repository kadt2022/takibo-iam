package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.model.OrganizationSignupDecision;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationSignupPolicyTest {

    private final OrganizationSignupPolicy policy =
            new OrganizationSignupPolicy();

    @Test
    void decides_to_create_a_new_organization_boundary() {
        assertThat(policy.decide(null, "Takibo IAM", "Takibo"))
                .isEqualTo(
                        new OrganizationSignupDecision.CreateNew(
                                "Takibo IAM",
                                "Takibo"
                        )
                );
    }

    @Test
    void rejects_reusing_an_existing_organization_boundary() {
        UUID organizationId = UUID.randomUUID();

        assertThat(policy.decide(
                organizationId,
                "ignored",
                "ignored"
        )).isEqualTo(
                new OrganizationSignupDecision
                        .ExistingOrganizationForbidden(organizationId)
        );
    }

    @Test
    void requires_code_and_name_for_a_new_boundary() {
        assertThatThrownBy(() -> policy.decide(null, " ", "Takibo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "organization.code is required when id is absent"
                );

        assertThatThrownBy(() ->
                policy.decide(null, "takibo", null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "organization.name is required when id is absent"
                );
    }
}
