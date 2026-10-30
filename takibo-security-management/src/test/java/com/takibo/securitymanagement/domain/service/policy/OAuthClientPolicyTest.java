package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
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
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.machine;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.orgToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.path;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.platformToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.spaceToken;
import static com.takibo.securitymanagement.domain.service.policy.PolicyFixtures.tokenWithPermissions;
import static org.assertj.core.api.Assertions.assertThat;

class OAuthClientPolicyTest {

    private static final String CLIENTS = "/api/v1/orgs/" + ORG + "/spaces/" + SPACE + "/clients";
    private static final String ROTATE = CLIENTS + "/" + CLIENT + "/rotate-secret";
    private static final String ADMIN_REQUIRED = "POL_OAUTH_CLIENT_ADMIN_REQUIRED";

    private final OAuthClientPolicy policy = new OAuthClientPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"R_SPACE_ADMIN", "R_SPACE_CLIENT_ADMIN", "R_ORG_CLIENT_ADMIN", "R_ORG_ADMIN"})
    void clientAdministratorOfTheTargetSpace_registersAndRotates(String role) {
        assertPermit(policy.evaluate(spaceToken(ORG, SPACE, role), path(CLIENTS), Action.CREATE), ADMIN_REQUIRED)
                .satisfies(d -> assertThat(d.getReason())
                        .isEqualTo("P_SPACE_CLIENTS_MANAGE holder bound to the target space may manage OAuth clients"));
        assertPermit(policy.evaluate(spaceToken(ORG, SPACE, role), path(ROTATE), Action.CREATE), ADMIN_REQUIRED)
                .satisfies(d -> assertThat(d.getReason()).isEqualTo(
                        "P_SPACE_CLIENTS_ROTATE_SECRET holder bound to the target space may manage OAuth clients"));
    }

    @Test
    void organizationAndSpaceAreMatchedIgnoringCase() {
        assertPermit(policy.evaluate(spaceToken(ORG.toUpperCase(), SPACE.toUpperCase(), "R_SPACE_ADMIN"),
                path(CLIENTS), Action.CREATE), ADMIN_REQUIRED);
    }

    @Test
    void eachPermissionGovernsItsOwnAct() {
        assertDeny(policy.evaluate(tokenWithPermissions("SPACE", ORG, SPACE, "P_SPACE_CLIENTS_MANAGE"),
                path(ROTATE), Action.CREATE), ADMIN_REQUIRED,
                "P_SPACE_CLIENTS_ROTATE_SECRET required to manage OAuth clients");
        assertDeny(policy.evaluate(tokenWithPermissions("SPACE", ORG, SPACE, "P_SPACE_CLIENTS_ROTATE_SECRET"),
                path(CLIENTS), Action.CREATE), ADMIN_REQUIRED, "P_SPACE_CLIENTS_MANAGE required to manage OAuth clients");
        assertDeny(policy.evaluate(spaceToken(ORG, SPACE, "R_SPACE_USER_ADMIN"), path(CLIENTS), Action.CREATE),
                ADMIN_REQUIRED);
    }

    @Test
    void onlyAHumanMayManageClients_evenWithAdminRoles() {
        assertDeny(policy.evaluate(machine(ORG, SPACE, "R_SPACE_ADMIN"), path(CLIENTS), Action.CREATE),
                ADMIN_REQUIRED, "Only a HUMAN subject may manage OAuth clients");
    }

    @Test
    void tokenOfAnotherOrganization_orWithoutOrganization_isDenied() {
        String reason = "Token organization does not match the target organization";
        assertDeny(policy.evaluate(spaceToken(OTHER_ORG, SPACE, "R_ORG_ADMIN"), path(CLIENTS), Action.CREATE),
                "POL_ORG_MISMATCH", reason);
        assertDeny(policy.evaluate(platformToken(), path(CLIENTS), Action.CREATE), "POL_ORG_MISMATCH", reason);
    }

    @Test
    void organizationScopedToken_isDenied_evenForTheOwner() {
        assertDeny(policy.evaluate(orgToken(ORG, "R_ORG_OWNER"), path(CLIENTS), Action.CREATE), ADMIN_REQUIRED,
                "A SPACE-scoped token is required");
    }

    @Test
    void tokenOfAnotherSpace_isDenied() {
        assertDeny(policy.evaluate(spaceToken(ORG, OTHER_SPACE, "R_SPACE_ADMIN"), path(CLIENTS), Action.CREATE),
                ADMIN_REQUIRED,
                "A SPACE-scoped token designating the target space is required to manage OAuth clients");
        assertDeny(policy.evaluate(tokenWithPermissions("SPACE", ORG, null, "P_SPACE_CLIENTS_MANAGE"), path(CLIENTS),
                Action.CREATE), ADMIN_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/" + CLIENT, "/" + CLIENT + "/rotate-secret/x", "/not-a-uuid/rotate-secret",
            "/" + CLIENT + "/disable"})
    void unknownClientSubRoutes_areNotGoverned_evenForTheAdmin(String suffix) {
        assertDeny(policy.evaluate(spaceToken(ORG, SPACE, "R_SPACE_ADMIN"), path(CLIENTS + suffix), Action.CREATE),
                "POL_OAUTH_CLIENT_ROUTE_NOT_GOVERNED", "No policy governs this OAuth client route yet");
    }

    @ParameterizedTest
    @EnumSource(value = Action.class, names = "CREATE", mode = EnumSource.Mode.EXCLUDE)
    void onlyCreateIsGoverned(Action action) {
        assertDeny(policy.evaluate(spaceToken(ORG, SPACE, "R_SPACE_ADMIN"), path(CLIENTS), action),
                "POL_OAUTH_CLIENT_ACTION_NOT_SUPPORTED", "Only CREATE is governed on the OAuth clients surface");
    }

    @Test
    void routesWithoutUuidOrganizationAndSpace_areOutsideTheSurface() {
        assertThat(policy.evaluate(spaceToken(ORG, SPACE, "R_SPACE_ADMIN"),
                path("/api/v1/orgs/acme/spaces/finance/clients"), Action.CREATE)).isEmpty();
        assertThat(policy.evaluate(spaceToken(ORG, SPACE, "R_SPACE_ADMIN"),
                path("/api/v1/orgs/" + ORG + "/spaces/finance/clients"), Action.CREATE)).isEmpty();
        assertSilentOutsideItsSurface(policy, spaceToken(ORG, SPACE, "R_SPACE_ADMIN"));
    }
}
