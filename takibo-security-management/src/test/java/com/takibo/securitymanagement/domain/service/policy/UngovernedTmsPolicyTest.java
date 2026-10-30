package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.ORG;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertDeny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertSilentOutsideItsSurface;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.orgToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.path;
import static org.assertj.core.api.Assertions.assertThat;

class UngovernedTmsPolicyTest {

    private final UngovernedTmsPolicy policy = new UngovernedTmsPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"", "/future-resource", "/spaces", "/dashboard/summary", "/members/x/y"})
    void everyTmsOrganizationRoute_isDenied_forEveryActionAndToken(String suffix) {
        // Verrou final : la chaîne n'y arrive que si aucune règle de surface n'a décidé.
        for (Action action : Action.values()) {
            assertDeny(policy.evaluate(orgToken(ORG, "R_ORG_OWNER"), path("/api/v1/orgs/" + ORG + suffix), action),
                    "POL_TMS_ROUTE_NOT_GOVERNED", "No explicit policy governs this TMS route - denied by default");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/orgs/acme/anything", "/api/v1/orgs/signup", "/api/v1/orgs", "/api/v2/orgs/" + ORG})
    void routesWithoutTmsOrganizationUuid_areOutsideTheLock(String route) {
        assertThat(policy.evaluate(orgToken(ORG, "R_ORG_OWNER"), path(route), Action.READ)).isEmpty();
    }

    @Test
    void isSilentOutsideItsSurface() {
        assertSilentOutsideItsSurface(policy, orgToken(ORG, "R_ORG_OWNER"));
    }
}
