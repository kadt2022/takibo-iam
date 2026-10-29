package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de chaque stratégie : une décision pour sa surface, aucune décision ailleurs.
 * Les décisions elles-mêmes sont couvertes par PolicyEvaluatorTest, à travers la chaîne.
 */
class ResourcePolicyTest {

    private static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String SPACE = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String CLIENT = "eeeeeeee-0000-0000-0000-000000000005";

    private static final Subject MEMBER = new Subject("actor", Set.of(), Set.of(),
            ORG, SPACE, "HUMAN", "SPACE", "cccccccc-0000-0000-0000-000000000003");

    private static final Map<ResourcePolicy, String> GOVERNED_PATH = Map.of(
            new OrganizationSignupPolicy(), "/api/v1/orgs/signup",
            new CurrentUserSpacesPolicy(), "/api/v1/me/spaces",
            new OrgDashboardPolicy(), "/api/v1/orgs/" + ORG + "/dashboard/summary",
            new ReadableSpacePolicy(), "/api/v1/orgs/acme/spaces/finance/users",
            new OAuthClientPolicy(), "/api/v1/orgs/" + ORG + "/spaces/" + SPACE + "/clients",
            new TmsSpacePolicy(), "/api/v1/orgs/" + ORG + "/spaces",
            new UngovernedTmsPolicy(), "/api/v1/orgs/" + ORG + "/future-resource");

    private static Optional<PolicyDecision> evaluate(ResourcePolicy policy, String path) {
        return policy.evaluate(MEMBER, new Resource(path, null, null), Action.READ);
    }

    @Test
    void eachPolicy_decidesOnItsSurface_andIsSilentElsewhere() {
        GOVERNED_PATH.forEach((policy, path) -> {
            String name = policy.getClass().getSimpleName();
            assertThat(evaluate(policy, path)).as(name + " " + path).isPresent();
            for (String outside : List.of("/api/v1/unknown", "/api/v1/orgs/acme/other", "/actuator/health")) {
                assertThat(evaluate(policy, outside)).as(name + " " + outside).isEmpty();
            }
        });
    }

    @Test
    void tmsSpacePolicy_leavesTheOAuthClientSurfaceToItsOwnPolicy() {
        String clients = "/api/v1/orgs/" + ORG + "/spaces/" + SPACE + "/clients";
        String rotate = clients + "/" + CLIENT + "/rotate-secret";

        assertThat(evaluate(new TmsSpacePolicy(), clients)).isEmpty();
        assertThat(evaluate(new TmsSpacePolicy(), rotate)).isEmpty();
        assertThat(evaluate(new OAuthClientPolicy(), rotate)).isPresent();
    }

    @Test
    void tenantIsolation_onlyDenies_neverPermits() {
        TenantIsolationPolicy isolation = new TenantIsolationPolicy();
        String otherOrg = "99999999-0000-0000-0000-000000000009";

        assertThat(isolation.evaluate(MEMBER, new Resource("/x", ORG, null), Action.READ)).isEmpty();
        assertThat(isolation.evaluate(MEMBER, new Resource("/x", null, null), Action.READ)).isEmpty();
        assertThat(isolation.evaluate(MEMBER, new Resource("/x", otherOrg, null), Action.READ))
                .hasValueSatisfying(decision -> {
                    assertThat(decision.isDeny()).isTrue();
                    assertThat(decision.getPolicyId()).isEqualTo("POL_ORG_MISMATCH");
                });
    }
}
