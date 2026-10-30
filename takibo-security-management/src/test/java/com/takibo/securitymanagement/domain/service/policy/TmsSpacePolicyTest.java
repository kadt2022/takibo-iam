package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.CLIENT;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.ORG;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.OTHER_ORG;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.OTHER_SPACE;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.SPACE;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertDeny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertPermit;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.assertSilentOutsideItsSurface;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.orgToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.path;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.platformToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.spaceToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.tokenWithPermissions;
import static org.assertj.core.api.Assertions.assertThat;

class TmsSpacePolicyTest {

    private static final String SPACES = "/api/v1/orgs/" + ORG + "/spaces";
    private static final String DETAIL = SPACES + "/" + SPACE;
    private static final String LIST = "POL_SPACE_LIST_ORG_AUTHORITY_REQUIRED";
    private static final String CREATE = "POL_SPACE_CREATE_ORG_AUTHORITY_REQUIRED";
    private static final String DETAIL_READ = "POL_SPACE_READ_ORG_OR_LOCAL_ADMIN_REQUIRED";
    private static final String ACTION_NOT_SUPPORTED = "POL_SPACE_ACTION_NOT_SUPPORTED";

    private final TmsSpacePolicy policy = new TmsSpacePolicy();

    @Nested
    class Collection {

        @ParameterizedTest
        @ValueSource(strings = {"R_ORG_OWNER", "R_ORG_ADMIN"})
        void organizationAuthority_listsAndCreates_withAnOrganizationToken(String role) {
            assertPermit(policy.evaluate(orgToken(ORG, role), path(SPACES), Action.READ), LIST)
                    .satisfies(d -> assertThat(d.getReason())
                            .isEqualTo("P_ORG_SPACES_READ holder may access the spaces collection"));
            assertPermit(policy.evaluate(orgToken(ORG, role), path(SPACES), Action.CREATE), CREATE)
                    .satisfies(d -> assertThat(d.getReason())
                            .isEqualTo("P_ORG_SPACES_CREATE holder may access the spaces collection"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"R_ORG_OWNER", "R_ORG_ADMIN"})
        void organizationAuthority_inASpaceToken_isDenied(String role) {
            // E3 : l'autorité ORG s'exerce avec un token ORGANIZATION.
            assertDeny(policy.evaluate(spaceToken(ORG, SPACE, role), path(SPACES), Action.READ), LIST,
                    "An ORGANIZATION-scoped token holding P_ORG_SPACES_READ is required");
            assertDeny(policy.evaluate(spaceToken(ORG, SPACE, role), path(SPACES), Action.CREATE), CREATE,
                    "An ORGANIZATION-scoped token holding P_ORG_SPACES_CREATE is required");
        }

        @Test
        void organizationPermissionForgedInASpaceToken_isDenied() {
            assertDeny(policy.evaluate(tokenWithPermissions("SPACE", ORG, SPACE, "P_ORG_SPACES_READ"),
                    path(SPACES), Action.READ), LIST);
        }

        @ParameterizedTest
        @ValueSource(strings = {"R_ORG_AUDITOR", "R_ORG_USER_ADMIN", "R_ORG_CLIENT_ADMIN"})
        void organizationRolesWithoutSpaceAuthority_areDenied(String role) {
            assertDeny(policy.evaluate(orgToken(ORG, role), path(SPACES), Action.READ), LIST);
            assertDeny(policy.evaluate(orgToken(ORG, role), path(SPACES), Action.CREATE), CREATE);
        }

        @ParameterizedTest
        @EnumSource(value = Action.class, names = {"UPDATE", "DELETE", "OTHER"})
        void onlyReadAndCreateAreGoverned(Action action) {
            assertDeny(policy.evaluate(orgToken(ORG, "R_ORG_OWNER"), path(SPACES), action), ACTION_NOT_SUPPORTED,
                    "Only READ and CREATE are governed on the spaces collection");
        }
    }

    @Nested
    class Detail {

        @Test
        void organizationAuthority_readsAnySpaceOfItsOrganization() {
            assertPermit(policy.evaluate(orgToken(ORG, "R_ORG_ADMIN"), path(SPACES + "/" + OTHER_SPACE), Action.READ),
                    DETAIL_READ)
                    .satisfies(d -> assertThat(d.getReason())
                            .isEqualTo("P_ORG_SPACES_READ holder may read the space detail"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"R_SPACE_ADMIN", "R_SPACE_AUDITOR", "R_SPACE_USER_ADMIN", "R_SPACE_CLIENT_ADMIN"})
        void spaceReader_readsTheSpaceItsTokenDesignates(String role) {
            assertPermit(policy.evaluate(spaceToken(ORG, SPACE, role), path(DETAIL), Action.READ), DETAIL_READ)
                    .satisfies(d -> assertThat(d.getReason())
                            .isEqualTo("P_SPACE_READ holder of this space may read its detail"));
            assertPermit(policy.evaluate(spaceToken(ORG, SPACE, role), path(SPACES + "/" + SPACE.toUpperCase()),
                    Action.READ), DETAIL_READ);
        }

        @Test
        void spaceReader_cannotReadAnotherSpace() {
            assertDeny(policy.evaluate(spaceToken(ORG, SPACE, "R_SPACE_ADMIN"), path(SPACES + "/" + OTHER_SPACE),
                    Action.READ), DETAIL_READ,
                    "P_ORG_SPACES_READ (ORGANIZATION token), or P_SPACE_READ for the token's own space");
        }

        @Test
        void memberWithoutReadPermission_isDenied() {
            assertDeny(policy.evaluate(spaceToken(ORG, SPACE), path(DETAIL), Action.READ), DETAIL_READ);
            assertDeny(policy.evaluate(orgToken(ORG), path(DETAIL), Action.READ), DETAIL_READ);
        }

        @ParameterizedTest
        @EnumSource(value = Action.class, names = "READ", mode = EnumSource.Mode.EXCLUDE)
        void onlyReadIsGoverned_evenForTheOwner(Action action) {
            assertDeny(policy.evaluate(orgToken(ORG, "R_ORG_OWNER"), path(DETAIL), action), ACTION_NOT_SUPPORTED,
                    "Only READ is governed on a space detail route");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/members", "/settings/x", "/not-a-uuid"})
    void subRoutes_areNotGoverned_evenForTheOwner(String suffix) {
        String route = suffix.equals("/not-a-uuid") ? SPACES + suffix : DETAIL + suffix;
        assertDeny(policy.evaluate(orgToken(ORG, "R_ORG_OWNER"), path(route), Action.READ),
                "POL_SPACE_ROUTE_NOT_GOVERNED", "No policy governs this space route yet — denied by default");
    }

    @Test
    void tokenOfAnotherOrganization_orWithoutOrganization_isDenied_beforeAnyOtherRule() {
        String reason = "Token organization does not match the target organization";
        assertDeny(policy.evaluate(orgToken(OTHER_ORG, "R_ORG_OWNER"), path(SPACES), Action.READ),
                "POL_ORG_MISMATCH", reason);
        assertDeny(policy.evaluate(orgToken(OTHER_ORG, "R_ORG_OWNER"), path(DETAIL + "/members"), Action.READ),
                "POL_ORG_MISMATCH", reason);
        assertDeny(policy.evaluate(platformToken(), path(SPACES), Action.READ), "POL_ORG_MISMATCH", reason);
    }

    @Test
    void leavesTheOAuthClientSurfaceToItsOwnPolicy() {
        String clients = DETAIL + "/clients";
        assertThat(policy.evaluate(spaceToken(ORG, SPACE, "R_SPACE_ADMIN"), path(clients), Action.CREATE)).isEmpty();
        assertThat(policy.evaluate(spaceToken(ORG, SPACE, "R_SPACE_ADMIN"),
                path(clients + "/" + CLIENT + "/rotate-secret"), Action.CREATE)).isEmpty();
    }

    @Test
    void readableCodeOrganizations_areOutsideTheSurface() {
        assertThat(policy.evaluate(orgToken(ORG, "R_ORG_OWNER"), path("/api/v1/orgs/acme/spaces"), Action.READ))
                .isEmpty();
        assertSilentOutsideItsSurface(policy, orgToken(ORG, "R_ORG_OWNER"));
    }
}
