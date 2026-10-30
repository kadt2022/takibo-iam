package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.Subject;
import com.takibo.securitymanagement.support.TokenPermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.ORG;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.SPACE;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertDeny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertPermit;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertSilentOutsideItsSurface;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.path;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.spaceToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.tokenWithPermissions;
import static org.assertj.core.api.Assertions.assertThat;

class ReadableSpacePolicyTest {

    private static final String SPACE_ROOT = "/api/v1/orgs/acme/spaces/finance";
    private static final String USERS = SPACE_ROOT + "/users";
    private static final String USER = USERS + "/" + PolicyFixtures.CLIENT;
    private static final String NOT_GOVERNED = "POL_SPACE_ROUTE_NOT_GOVERNED";

    private final ReadableSpacePolicy policy = new ReadableSpacePolicy();

    /** Chaque paire route/action gouvernée : la permission exacte qu'elle exige et son policyId. */
    static Stream<Arguments> governedPairs() {
        return Stream.of(
                Arguments.of(USERS, Action.READ, "P_SPACE_USERS_READ", "POL_USER_ADMIN_REQUIRED"),
                Arguments.of(USERS, Action.CREATE, "P_SPACE_USERS_MANAGE", "POL_USER_ADMIN_REQUIRED"),
                Arguments.of(USER, Action.READ, "P_SPACE_USERS_READ", "POL_USER_ADMIN_REQUIRED"),
                Arguments.of(USER, Action.UPDATE, "P_SPACE_USERS_MANAGE", "POL_USER_ADMIN_REQUIRED"),
                Arguments.of(USER + "/suspend", Action.CREATE, "P_SPACE_USERS_LIFECYCLE", "POL_USER_ADMIN_REQUIRED"),
                Arguments.of(USER + "/activate", Action.CREATE, "P_SPACE_USERS_LIFECYCLE", "POL_USER_ADMIN_REQUIRED"),
                Arguments.of(USER + "/lock", Action.CREATE, "P_SPACE_USERS_LIFECYCLE", "POL_USER_ADMIN_REQUIRED"),
                Arguments.of(USER + "/deactivate", Action.CREATE, "P_SPACE_USERS_LIFECYCLE", "POL_USER_ADMIN_REQUIRED"),
                Arguments.of(USER + "/roles", Action.READ, "P_SPACE_RBAC_READ", "POL_USER_RBAC_ADMIN_REQUIRED"),
                Arguments.of(USER + "/roles", Action.CREATE, "P_SPACE_RBAC_ASSIGN", "POL_USER_RBAC_ADMIN_REQUIRED"),
                Arguments.of(USER + "/groups", Action.READ, "P_SPACE_RBAC_READ", "POL_USER_RBAC_ADMIN_REQUIRED"),
                Arguments.of(USER + "/groups", Action.CREATE, "P_SPACE_RBAC_ASSIGN", "POL_USER_RBAC_ADMIN_REQUIRED"),
                Arguments.of(USER + "/roles/R_SPACE_ADMIN", Action.DELETE, "P_SPACE_RBAC_ASSIGN",
                        "POL_USER_RBAC_ADMIN_REQUIRED"),
                Arguments.of(USER + "/groups/G1", Action.DELETE, "P_SPACE_RBAC_ASSIGN", "POL_USER_RBAC_ADMIN_REQUIRED"),
                Arguments.of(SPACE_ROOT + "/roles", Action.READ, "P_SPACE_RBAC_READ", "POL_RBAC_READ_ADMIN_REQUIRED"),
                Arguments.of(SPACE_ROOT + "/roles/R1", Action.READ, "P_SPACE_RBAC_READ", "POL_RBAC_READ_ADMIN_REQUIRED"),
                Arguments.of(SPACE_ROOT + "/groups", Action.READ, "P_SPACE_RBAC_READ", "POL_RBAC_READ_ADMIN_REQUIRED"),
                Arguments.of(SPACE_ROOT + "/permissions/P1", Action.READ, "P_SPACE_RBAC_READ",
                        "POL_RBAC_READ_ADMIN_REQUIRED"));
    }

    @ParameterizedTest(name = "{1} {0} exige {2}")
    @MethodSource("governedPairs")
    void spaceTokenHoldingTheExactPermission_isPermitted(String route, Action action, String permission,
                                                         String policyId) {
        Subject holder = tokenWithPermissions("SPACE", ORG, SPACE, permission);

        assertPermit(policy.evaluate(holder, path(route), action), policyId)
                .satisfies(d -> assertThat(d.getReason()).isEqualTo(permission + " granted in the token space"));
    }

    @ParameterizedTest(name = "{1} {0} sans {2}")
    @MethodSource("governedPairs")
    void spaceTokenHoldingEveryOtherSpacePermission_isDenied(String route, Action action, String permission,
                                                             String policyId) {
        Set<String> others = new HashSet<>(TokenPermissions.of(Set.of("R_SPACE_ADMIN"), ORG, SPACE, "SPACE"));
        assertThat(others.remove(permission)).isTrue();
        Subject withoutIt = tokenWithPermissions("SPACE", ORG, SPACE, others.toArray(String[]::new));

        assertDeny(policy.evaluate(withoutIt, path(route), action), policyId,
                "A SPACE-scoped token holding " + permission + " is required");
    }

    @ParameterizedTest(name = "{1} {0} hors token SPACE")
    @MethodSource("governedPairs")
    void thePermissionOutsideASpaceToken_isDenied(String route, Action action, String permission, String policyId) {
        assertDeny(policy.evaluate(tokenWithPermissions("ORGANIZATION", ORG, null, permission), path(route), action),
                policyId);
        assertDeny(policy.evaluate(tokenWithPermissions("SPACE", ORG, null, permission), path(route), action),
                policyId);
    }

    static Stream<Arguments> ungovernedPairs() {
        return Stream.of(
                Arguments.of(USER, Action.DELETE),
                Arguments.of(USERS, Action.UPDATE),
                Arguments.of(USERS, Action.OTHER),
                Arguments.of(USER + "/suspend", Action.UPDATE),
                Arguments.of(USER + "/roles/R_SPACE_ADMIN", Action.CREATE),
                Arguments.of(USER + "/groups", Action.UPDATE),
                Arguments.of(USER + "/unknown", Action.READ),
                Arguments.of(USER + "/groups/G1/members", Action.READ),
                Arguments.of(SPACE_ROOT + "/roles", Action.CREATE),
                Arguments.of(SPACE_ROOT + "/permissions/P1", Action.DELETE),
                Arguments.of(SPACE_ROOT + "/roles/R1/members", Action.READ));
    }

    @ParameterizedTest(name = "{1} {0}")
    @MethodSource("ungovernedPairs")
    void ungovernedPairs_areDeniedForEveryone_adminsIncluded(String route, Action action) {
        assertDeny(policy.evaluate(spaceToken(ORG, SPACE, "R_SPACE_ADMIN"), path(route), action), NOT_GOVERNED,
                "No policy governs this space route and action — denied by default");
    }

    @Test
    void specializedRoles_holdOnlyTheirSurface_withoutEscalation() {
        Subject userAdmin = spaceToken(ORG, SPACE, "R_SPACE_USER_ADMIN");

        assertPermit(policy.evaluate(userAdmin, path(USERS), Action.CREATE), "POL_USER_ADMIN_REQUIRED");
        assertDeny(policy.evaluate(userAdmin, path(USER + "/roles"), Action.CREATE), "POL_USER_RBAC_ADMIN_REQUIRED");
        assertDeny(policy.evaluate(spaceToken(ORG, SPACE, "R_SPACE_AUDITOR"), path(USERS), Action.READ),
                "POL_USER_ADMIN_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/orgs/" + ORG + "/spaces/" + SPACE + "/users",
            SPACE_ROOT,
            SPACE_ROOT + "/clients",
            "/api/v1/orgs/acme/members/finance/users"})
    void routesOutsideTheReadableSpaceSurface_areNotItsConcern(String route) {
        assertThat(policy.evaluate(spaceToken(ORG, SPACE, "R_SPACE_ADMIN"), path(route), Action.READ)).isEmpty();
    }

    @Test
    void isSilentOutsideItsSurface() {
        assertSilentOutsideItsSurface(policy, spaceToken(ORG, SPACE, "R_SPACE_ADMIN"));
    }
}
