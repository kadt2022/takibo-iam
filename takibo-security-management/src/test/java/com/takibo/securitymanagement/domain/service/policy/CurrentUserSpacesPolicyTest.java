package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.Subject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.ACCOUNT;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.ORG;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.SPACE;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertDeny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertPermit;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertSilentOutsideItsSurface;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.machine;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.orgToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.path;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.platformToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.spaceToken;
import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserSpacesPolicyTest {

    private static final String MY_SPACES = "/api/v1/me/spaces";
    private static final String POLICY_ID = "POL_MY_SPACES_ORG_HUMAN_REQUIRED";

    private final CurrentUserSpacesPolicy policy = new CurrentUserSpacesPolicy();

    private static Subject orgHuman(String orgId, String spaceId, String accountId) {
        return new Subject("actor", Set.of(), Set.of(), orgId, spaceId, "HUMAN", "ORGANIZATION", accountId);
    }

    @Test
    void organizationHumanAccount_readsItsSpaces_withoutAnyRole() {
        assertPermit(policy.evaluate(orgToken(ORG), path(MY_SPACES), Action.READ), POLICY_ID)
                .satisfies(d -> assertThat(d.getReason())
                        .isEqualTo("Organization-scoped human account token may list its own accessible spaces"));
        assertPermit(policy.evaluate(orgToken(ORG, "R_ORG_OWNER"), path(MY_SPACES), Action.READ), POLICY_ID);
    }

    static Stream<Arguments> nonOrganizationAccounts() {
        return Stream.of(
                Arguments.of("token SPACE", spaceToken(ORG, SPACE, "R_SPACE_ADMIN")),
                Arguments.of("token PLATFORM", platformToken()),
                Arguments.of("token machine", machine(ORG, SPACE)),
                Arguments.of("sans compte", orgHuman(ORG, null, null)),
                Arguments.of("compte vide", orgHuman(ORG, null, " ")),
                Arguments.of("organisation vide", orgHuman(" ", null, ACCOUNT)),
                Arguments.of("token ORGANIZATION portant un space", orgHuman(ORG, SPACE, ACCOUNT)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonOrganizationAccounts")
    void anythingButAnOrganizationHumanAccount_isDenied(String label, Subject subject) {
        assertDeny(policy.evaluate(subject, path(MY_SPACES), Action.READ), POLICY_ID,
                "Organization-scoped human account token required to list accessible spaces");
    }

    @ParameterizedTest
    @EnumSource(value = Action.class, names = "READ", mode = EnumSource.Mode.EXCLUDE)
    void onlyReadIsGoverned(Action action) {
        assertDeny(policy.evaluate(orgToken(ORG), path(MY_SPACES), action), POLICY_ID);
    }

    @Test
    void isSilentOutsideItsSurface() {
        assertThat(policy.evaluate(orgToken(ORG), path(MY_SPACES + "/" + SPACE), Action.READ)).isEmpty();
        assertSilentOutsideItsSurface(policy, orgToken(ORG));
    }
}
