package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.ORG;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertDeny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertPermit;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertSilentOutsideItsSurface;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.machine;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.orgToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.path;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.platformToken;
import static org.assertj.core.api.Assertions.assertThat;

class OrganizationSignupPolicyTest {

    private static final String SIGNUP = "/api/v1/orgs/signup";

    private final OrganizationSignupPolicy policy = new OrganizationSignupPolicy();

    @Test
    void create_isPermittedToAnyAuthenticatedCaller_withoutRole() {
        // L'authentification est exigée en amont ; la règle ne lit ni rôle ni frontière.
        for (var caller : java.util.List.of(orgToken(ORG), platformToken(), machine(null, null))) {
            assertPermit(policy.evaluate(caller, path(SIGNUP), Action.CREATE), "POL_ORG_SIGNUP_AUTHENTICATED_CREATE")
                    .satisfies(d -> assertThat(d.getReason())
                            .isEqualTo("An authenticated caller may bootstrap a new organization"));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Action.class, names = "CREATE", mode = EnumSource.Mode.EXCLUDE)
    void everyOtherAction_isDenied(Action action) {
        assertDeny(policy.evaluate(orgToken(ORG, "R_ORG_OWNER"), path(SIGNUP), action),
                "POL_ORG_SIGNUP_ACTION_NOT_SUPPORTED", "Only CREATE is governed on the organization signup route");
    }

    @ParameterizedTest
    @ValueSource(strings = {SIGNUP + "/", SIGNUP + "x", "/api/v1/orgs/signup/confirm"})
    void onlyTheExactSignupPath_isGoverned(String nearMiss) {
        assertThat(policy.evaluate(orgToken(ORG), path(nearMiss), Action.CREATE)).isEmpty();
    }

    @Test
    void isSilentOutsideItsSurface() {
        assertSilentOutsideItsSurface(policy, orgToken(ORG, "R_ORG_OWNER"));
    }
}
