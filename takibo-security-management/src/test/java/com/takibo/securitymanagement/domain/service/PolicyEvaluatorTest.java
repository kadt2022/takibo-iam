package com.takibo.securitymanagement.domain.service;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.Environment;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEvaluatorTest {

    private static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String SPACE = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String READABLE_USERS_PATH = "/api/v1/orgs/takibo-iam/spaces/finance/users";

    private final PolicyEvaluator evaluator = new PolicyEvaluator();

    private Subject subject(Set<String> roles) {
        return new Subject("actor", roles, Set.of(), ORG, SPACE);
    }

    private PolicyDecision evaluateUsersRoute(Set<String> roles, String path, Action action) {
        return evaluator.evaluate(
                subject(roles),
                new Resource(path, ORG, SPACE),
                action,
                new Environment(Instant.now(), "127.0.0.1", 0));
    }

    private PolicyDecision evaluateCreateUser(Set<String> roles) {
        return evaluateUsersRoute(roles, READABLE_USERS_PATH, Action.CREATE);
    }

    @Test
    void realTechnicalCodes_areRecognizedAsTenantAdmin() {
        assertThat(evaluateCreateUser(Set.of("R_SPACE_ADMIN")).isDeny()).isFalse();
        assertThat(evaluateCreateUser(Set.of("R_ORG_OWNER")).isDeny()).isFalse();
        assertThat(evaluateCreateUser(Set.of("R_ORG_ADMIN")).isDeny()).isFalse();
        assertThat(evaluateCreateUser(Set.of("R_PLATFORM_ADMIN")).isDeny()).isFalse();
        assertThat(evaluateCreateUser(Set.of("R_ORG_OWNER", "R_SPACE_ADMIN")).isDeny()).isFalse();
    }

    @Test
    void legacyAliases_remainAccepted() {
        assertThat(evaluateCreateUser(Set.of("SPACE_ADMIN")).isDeny()).isFalse();
        assertThat(evaluateCreateUser(Set.of("ORG_ADMIN")).isDeny()).isFalse();
        assertThat(evaluateCreateUser(Set.of("PLATFORM_ADMIN")).isDeny()).isFalse();
    }

    @Test
    void readableRoute_userCreation_deniedWithoutAdminRole() {
        PolicyDecision decision = evaluateCreateUser(Set.of());

        assertThat(decision.isDeny()).isTrue();
        assertThat(decision.getPolicyId()).isEqualTo("POL_USER_ADMIN_REQUIRED");
    }

    @Test
    void readableRoute_userRead_deniedWithoutAdminRole() {
        // La lecture de l'annuaire d'un space est aussi un acte d'admin (PR #24).
        PolicyDecision list = evaluateUsersRoute(Set.of(), READABLE_USERS_PATH, Action.READ);
        PolicyDecision get = evaluateUsersRoute(Set.of(),
                READABLE_USERS_PATH + "/dddddddd-0000-0000-0000-000000000004", Action.READ);

        assertThat(list.isDeny()).isTrue();
        assertThat(list.getPolicyId()).isEqualTo("POL_USER_ADMIN_REQUIRED");
        assertThat(get.isDeny()).isTrue();
    }

    @Test
    void readableRoute_userUpdateAndLifecycle_deniedWithoutAdminRole() {
        String userPath = READABLE_USERS_PATH + "/dddddddd-0000-0000-0000-000000000004";

        assertThat(evaluateUsersRoute(Set.of(), userPath, Action.UPDATE).isDeny()).isTrue();
        assertThat(evaluateUsersRoute(Set.of(), userPath + "/suspend", Action.CREATE).isDeny()).isTrue();
        assertThat(evaluateUsersRoute(Set.of(), userPath + "/activate", Action.CREATE).isDeny()).isTrue();
        assertThat(evaluateUsersRoute(Set.of(), userPath + "/lock", Action.CREATE).isDeny()).isTrue();
        assertThat(evaluateUsersRoute(Set.of(), userPath + "/deactivate", Action.CREATE).isDeny()).isTrue();
    }

    @Test
    void readableRoute_readAndLifecycle_allowedWithAdminRole() {
        String userPath = READABLE_USERS_PATH + "/dddddddd-0000-0000-0000-000000000004";

        assertThat(evaluateUsersRoute(Set.of("R_SPACE_ADMIN"), READABLE_USERS_PATH, Action.READ).isDeny()).isFalse();
        assertThat(evaluateUsersRoute(Set.of("R_SPACE_ADMIN"), userPath, Action.UPDATE).isDeny()).isFalse();
        assertThat(evaluateUsersRoute(Set.of("R_ORG_OWNER"), userPath + "/suspend", Action.CREATE).isDeny()).isFalse();
    }

    @Test
    void userRbacGovernanceRoutes_deniedWithDedicatedPolicy() {
        // Déléguer/retirer le pouvoir d'un user a sa policy dédiée (PR #26),
        // plus spécifique que la règle générale users.
        String base = READABLE_USERS_PATH + "/dddddddd-0000-0000-0000-000000000004";

        for (String path : new String[]{
                base + "/roles",
                base + "/roles/R_SPACE_ADMIN",
                base + "/groups",
                base + "/groups/G_SPACE_ADMINS"}) {
            PolicyDecision decision = evaluateUsersRoute(Set.of(), path, Action.CREATE);
            assertThat(decision.isDeny()).as(path).isTrue();
            assertThat(decision.getPolicyId()).as(path).isEqualTo("POL_USER_RBAC_ADMIN_REQUIRED");
        }
    }

    @Test
    void userRbacGovernanceRoutes_allowedWithAdminRole() {
        String base = READABLE_USERS_PATH + "/dddddddd-0000-0000-0000-000000000004";

        assertThat(evaluateUsersRoute(Set.of("R_SPACE_ADMIN"), base + "/roles", Action.CREATE).isDeny()).isFalse();
        assertThat(evaluateUsersRoute(Set.of("R_ORG_OWNER"), base + "/roles/R_SPACE_ADMIN", Action.DELETE)
                .isDeny()).isFalse();
        assertThat(evaluateUsersRoute(Set.of("R_ORG_ADMIN"), base + "/groups", Action.READ).isDeny()).isFalse();
    }

    @Test
    void readableRbacCatalogRoutes_deniedWithoutAdminRole() {
        // Le catalogue RBAC décrit la structure du pouvoir : lecture réservée aux admins (PR #25).
        String base = "/api/v1/orgs/takibo-iam/spaces/finance";

        for (String path : new String[]{
                base + "/roles",
                base + "/roles/R_SPACE_ADMIN",
                base + "/groups",
                base + "/groups/G_SPACE_ADMINS",
                base + "/permissions",
                base + "/permissions/P_MANAGE_USERS"}) {
            PolicyDecision decision = evaluateUsersRoute(Set.of(), path, Action.READ);
            assertThat(decision.isDeny()).as(path).isTrue();
            assertThat(decision.getPolicyId()).as(path).isEqualTo("POL_RBAC_READ_ADMIN_REQUIRED");
        }
    }

    @Test
    void readableRbacCatalogRoutes_allowedWithAdminRole() {
        String base = "/api/v1/orgs/takibo-iam/spaces/finance";

        assertThat(evaluateUsersRoute(Set.of("R_SPACE_ADMIN"), base + "/roles", Action.READ).isDeny()).isFalse();
        assertThat(evaluateUsersRoute(Set.of("R_ORG_OWNER"), base + "/groups", Action.READ).isDeny()).isFalse();
        assertThat(evaluateUsersRoute(Set.of("R_ORG_ADMIN"), base + "/permissions", Action.READ).isDeny()).isFalse();
    }

    @Test
    void signupRoute_isNotCaughtByUsersRule() {
        PolicyDecision decision = evaluateUsersRoute(Set.of(), "/api/v1/orgs/signup", Action.CREATE);

        assertThat(decision.isDeny()).isFalse();
    }

    @Test
    void uuidRoute_userCreation_stillRequiresAdminRole() {
        PolicyDecision decision = evaluator.evaluate(
                subject(Set.of()),
                new Resource("/api/spaces/" + SPACE + "/users", ORG, SPACE),
                Action.CREATE,
                new Environment(Instant.now(), "127.0.0.1", 0));

        assertThat(decision.isDeny()).isTrue();
    }

    @Test
    void orgMismatch_alwaysDenied_evenForOrgOwner() {
        PolicyDecision decision = evaluator.evaluate(
                new Subject("actor", Set.of("R_ORG_OWNER"), Set.of(), ORG, SPACE),
                new Resource(READABLE_USERS_PATH, "99999999-0000-0000-0000-000000000009", SPACE),
                Action.CREATE,
                new Environment(Instant.now(), "127.0.0.1", 0));

        assertThat(decision.isDeny()).isTrue();
        assertThat(decision.getPolicyId()).isEqualTo("POL_ORG_MISMATCH");
    }
}
