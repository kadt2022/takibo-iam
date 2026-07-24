package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.exception.OrganizationDisabledException;
import com.takibo.managementservice.domain.exception.SpaceQuotaExceededException;
import com.takibo.managementservice.domain.model.OrganizationContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceCreationEligibilityPolicyTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "aaaaaaaa-0000-0000-0000-000000000001"
    );

    private final SpaceCreationEligibilityPolicy policy =
            new SpaceCreationEligibilityPolicy();

    @Test
    void accepts_an_enabled_organization_below_quota() {
        OrganizationContext eligibleOrganization =
                new OrganizationContext(ORGANIZATION_ID, true, 9);

        assertThatCode(() ->
                policy.validateEligibility(eligibleOrganization)
        ).doesNotThrowAnyException();
    }

    @Test
    void rejects_a_disabled_organization_before_other_rules() {
        OrganizationContext disabledOrganization =
                new OrganizationContext(ORGANIZATION_ID, false, 12);

        assertThatThrownBy(() ->
                policy.validateEligibility(disabledOrganization)
        )
                .isInstanceOf(OrganizationDisabledException.class)
                .hasMessage("Organization is disabled: " + ORGANIZATION_ID);
    }

    @Test
    void rejects_an_organization_at_quota_with_current_usage() {
        int currentSpaces = 12;
        OrganizationContext organizationAtQuota =
                new OrganizationContext(
                        ORGANIZATION_ID,
                        true,
                        currentSpaces
                );

        assertThatThrownBy(() ->
                policy.validateEligibility(organizationAtQuota)
        )
                .isInstanceOf(SpaceQuotaExceededException.class)
                .hasMessage(
                        "Space quota exceeded for orgId=" + ORGANIZATION_ID
                                + " max=10 current=" + currentSpaces
                );
    }
}
