package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.Subject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.ORG;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.OTHER_ORG;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.SPACE;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertDeny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertPermit;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertSilentOutsideItsSurface;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.machine;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.orgToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.path;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.platformToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.spaceToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.tokenWithPermissions;
import static org.assertj.core.api.Assertions.assertThat;

class OrgDashboardPolicyTest {

    private static final String SUMMARY = "/api/v1/orgs/" + ORG + "/dashboard/summary";
    private static final String ADMIN_REQUIRED = "POL_ORG_DASHBOARD_ADMIN_REQUIRED";
    private static final String ORG_HUMAN_REQUIRED = "POL_ORG_DASHBOARD_ORG_HUMAN_REQUIRED";

    private final OrgDashboardPolicy policy = new OrgDashboardPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"R_ORG_OWNER", "R_ORG_ADMIN", "R_ORG_AUDITOR", "R_ORG_USER_ADMIN", "R_ORG_CLIENT_ADMIN"})
    void organizationRoleHoldingOrgRead_readsTheSummary(String role) {
        assertPermit(policy.evaluate(orgToken(ORG, role), path(SUMMARY), Action.READ), ADMIN_REQUIRED)
                .satisfies(d -> assertThat(d.getReason())
                        .isEqualTo("P_ORG_READ holder of the target organization may read the dashboard summary"));
    }

    @Test
    void targetOrganizationIsMatchedIgnoringCase() {
        String upperCasePath = "/api/v1/orgs/" + ORG.toUpperCase() + "/dashboard/summary";
        assertPermit(policy.evaluate(orgToken(ORG, "R_ORG_ADMIN"), path(upperCasePath), Action.READ), ADMIN_REQUIRED);
    }

    static Stream<Arguments> notAnOrganizationHumanOfTheTarget() {
        return Stream.of(
                Arguments.of("token SPACE", spaceToken(ORG, SPACE, "R_ORG_ADMIN")),
                Arguments.of("token d'une autre organisation", orgToken(OTHER_ORG, "R_ORG_ADMIN")),
                Arguments.of("token PLATFORM", platformToken()),
                Arguments.of("token machine", machine(ORG, SPACE, "R_SPACE_ADMIN")),
                Arguments.of("P_ORG_READ forgée dans un token SPACE",
                        tokenWithPermissions("SPACE", ORG, SPACE, "P_ORG_READ")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notAnOrganizationHumanOfTheTarget")
    void anythingButAnOrganizationHumanOfTheTarget_isDenied(String label, Subject subject) {
        assertDeny(policy.evaluate(subject, path(SUMMARY), Action.READ), ORG_HUMAN_REQUIRED,
                "Organization-scoped human token matching the target organization is required");
    }

    @Test
    void organizationMemberWithoutOrgRead_isDenied() {
        assertDeny(policy.evaluate(orgToken(ORG), path(SUMMARY), Action.READ), ADMIN_REQUIRED,
                "P_ORG_READ required to read the organization dashboard");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/dashboard/other", "/dashboard/summary/details"})
    void otherDashboardRoutes_areNotGoverned_evenForAnAdmin(String suffix) {
        assertDeny(policy.evaluate(orgToken(ORG, "R_ORG_ADMIN"), path("/api/v1/orgs/" + ORG + suffix), Action.READ),
                "POL_ORG_DASHBOARD_ROUTE_NOT_GOVERNED",
                "No policy governs this dashboard route yet — denied by default");
    }

    @ParameterizedTest
    @EnumSource(value = Action.class, names = "READ", mode = EnumSource.Mode.EXCLUDE)
    void onlyReadIsGovernedOnTheSummary(Action action) {
        assertDeny(policy.evaluate(orgToken(ORG, "R_ORG_ADMIN"), path(SUMMARY), action),
                "POL_ORG_DASHBOARD_ACTION_NOT_SUPPORTED",
                "Only READ is governed on the organization dashboard summary");
    }

    @Test
    void readableCodeOrganizations_areOutsideTheSurface() {
        assertThat(policy.evaluate(orgToken(ORG, "R_ORG_ADMIN"), path("/api/v1/orgs/acme/dashboard/summary"),
                Action.READ)).isEmpty();
        assertSilentOutsideItsSurface(policy, orgToken(ORG, "R_ORG_ADMIN"));
    }
}
