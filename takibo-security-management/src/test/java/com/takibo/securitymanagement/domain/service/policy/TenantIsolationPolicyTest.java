package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.Resource;
import org.junit.jupiter.api.Test;

import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.ORG;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.OTHER_ORG;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.SPACE;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertDeny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.orgToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.platformToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.spaceToken;
import static org.assertj.core.api.Assertions.assertThat;

class TenantIsolationPolicyTest {

    private static final String MISMATCH = "POL_ORG_MISMATCH";
    private static final String REASON = "User does not belong to target organization";

    private final TenantIsolationPolicy policy = new TenantIsolationPolicy();

    private static Resource targeting(String orgId) {
        return new Resource("/api/v1/orgs/" + orgId + "/spaces", orgId, null);
    }

    @Test
    void tokenOfTheTargetOrganization_isLeftToTheSurfaceRules() {
        assertThat(policy.evaluate(orgToken(ORG, "R_ORG_OWNER"), targeting(ORG), Action.READ)).isEmpty();
        assertThat(policy.evaluate(spaceToken(ORG, SPACE), targeting(ORG), Action.CREATE)).isEmpty();
    }

    @Test
    void organizationsAreComparedAsUuids_caseIgnored() {
        assertThat(policy.evaluate(orgToken(ORG.toUpperCase()), targeting(ORG), Action.READ)).isEmpty();
    }

    @Test
    void tokenOfAnotherOrganization_isDenied_whateverItsRoles() {
        assertDeny(policy.evaluate(orgToken(OTHER_ORG, "R_ORG_OWNER"), targeting(ORG), Action.READ), MISMATCH, REASON);
    }

    @Test
    void tokenWithoutOrganization_isDenied() {
        assertDeny(policy.evaluate(platformToken(), targeting(ORG), Action.READ), MISMATCH, REASON);
    }

    @Test
    void malformedTokenOrganization_isDenied() {
        assertDeny(policy.evaluate(orgToken("not-a-uuid"), targeting(ORG), Action.READ), MISMATCH, REASON);
    }

    @Test
    void resourceWithoutOrganization_isOutsideItsScope() {
        assertThat(policy.evaluate(platformToken(), new Resource("/api/v1/me/spaces", null, null), Action.READ))
                .isEmpty();
    }
}
