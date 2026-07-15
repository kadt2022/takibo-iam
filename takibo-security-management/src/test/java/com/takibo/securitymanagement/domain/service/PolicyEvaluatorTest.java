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
    private static final String ACCOUNT = "cccccccc-0000-0000-0000-000000000003";
    private static final String READABLE_USERS_PATH = "/api/v1/orgs/takibo-iam/spaces/finance/users";

    private final PolicyEvaluator evaluator = new PolicyEvaluator();

    private Subject subject(Set<String> roles) {
        return new Subject("actor", roles, Set.of(), ORG, SPACE);
    }

    private Subject orgHuman(Set<String> roles) {
        return new Subject("actor", roles, Set.of(), ORG, null, "HUMAN", "ORGANIZATION", ACCOUNT);
    }

    private PolicyDecision evaluateMySpaces(Subject subject) {
        return evaluator.evaluate(
                subject,
                new Resource("/api/v1/me/spaces", null, null),
                Action.READ,
                new Environment(Instant.now(), "127.0.0.1", 0));
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
    void currentUserSpaces_allowsOrganizationScopedHumanWithoutRole() {
        PolicyDecision decision = evaluateMySpaces(orgHuman(Set.of()));

        assertThat(decision.isDeny()).isFalse();
        assertThat(decision.getPolicyId()).isEqualTo("POL_MY_SPACES_ORG_HUMAN_REQUIRED");
    }

    @Test
    void currentUserSpaces_orgOwnerStillUsesPersonalSurface() {
        PolicyDecision decision = evaluateMySpaces(orgHuman(Set.of("R_ORG_OWNER")));

        assertThat(decision.isDeny()).isFalse();
        assertThat(decision.getPolicyId()).isEqualTo("POL_MY_SPACES_ORG_HUMAN_REQUIRED");
    }

    @Test
    void currentUserSpaces_deniesPlatformOrServiceTokens() {
        for (Subject subject : java.util.List.of(
                new Subject("platform", Set.of("R_PLATFORM_ADMIN"), Set.of(), ORG, null,
                        "PLATFORM", "ORGANIZATION", null),
                new Subject("client", Set.of(), Set.of(), ORG, null,
                        "SERVICE", "ORGANIZATION", null))) {
            PolicyDecision decision = evaluateMySpaces(subject);

            assertThat(decision.isDeny()).isTrue();
            assertThat(decision.getPolicyId()).isEqualTo("POL_MY_SPACES_ORG_HUMAN_REQUIRED");
        }
    }

    @Test
    void currentUserSpaces_deniesSpaceToken() {
        Subject spaceToken = new Subject("actor", Set.of(), Set.of(), ORG, SPACE,
                "HUMAN", "SPACE", ACCOUNT);

        PolicyDecision decision = evaluateMySpaces(spaceToken);

        assertThat(decision.isDeny()).isTrue();
        assertThat(decision.getPolicyId()).isEqualTo("POL_MY_SPACES_ORG_HUMAN_REQUIRED");
    }

    @Test
    void currentUserSpaces_deniesTokenWithoutOrgOrAccount() {
        for (Subject subject : java.util.List.of(
                new Subject("actor", Set.of(), Set.of(), null, null,
                        "HUMAN", "ORGANIZATION", ACCOUNT),
                new Subject("actor", Set.of(), Set.of(), ORG, null,
                        "HUMAN", "ORGANIZATION", null))) {
            PolicyDecision decision = evaluateMySpaces(subject);

            assertThat(decision.isDeny()).isTrue();
            assertThat(decision.getPolicyId()).isEqualTo("POL_MY_SPACES_ORG_HUMAN_REQUIRED");
        }
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

    // ─────────────────────────────────────────────────────────────
    // Surface spaces TMS : /api/v1/orgs/{uuid}/spaces[/{uuid}] (PR #31)
    // ─────────────────────────────────────────────────────────────

    private static final String TMS_SPACES_PATH = "/api/v1/orgs/" + ORG + "/spaces";
    private static final String OTHER_SPACE = "cccccccc-0000-0000-0000-000000000003";

    private PolicyDecision evaluateTmsRoute(Subject subject, String path, Action action) {
        // Câblage prod : Resource porte le path seul (orgId/spaceId null) —
        // la frontière org se lit dans le chemin.
        return evaluator.evaluate(subject, new Resource(path, null, null), action,
                new Environment(Instant.now(), "127.0.0.1", 0));
    }

    @Test
    void tmsSpaceList_requiresOrgAuthority() {
        assertThat(evaluateTmsRoute(subject(Set.of("R_ORG_OWNER")), TMS_SPACES_PATH, Action.READ).isDeny()).isFalse();
        assertThat(evaluateTmsRoute(subject(Set.of("R_ORG_ADMIN")), TMS_SPACES_PATH, Action.READ).isDeny()).isFalse();

        for (Set<String> roles : java.util.List.of(Set.of("R_SPACE_ADMIN"), Set.<String>of())) {
            PolicyDecision decision = evaluateTmsRoute(subject(roles), TMS_SPACES_PATH, Action.READ);
            assertThat(decision.isDeny()).as(roles.toString()).isTrue();
            assertThat(decision.getPolicyId()).isEqualTo("POL_SPACE_LIST_ORG_AUTHORITY_REQUIRED");
        }
    }

    @Test
    void tmsSpaceCreate_requiresOrgAuthority() {
        assertThat(evaluateTmsRoute(subject(Set.of("R_ORG_OWNER")), TMS_SPACES_PATH, Action.CREATE).isDeny()).isFalse();
        assertThat(evaluateTmsRoute(subject(Set.of("R_ORG_ADMIN")), TMS_SPACES_PATH, Action.CREATE).isDeny()).isFalse();

        // R_SPACE_ADMIN ne crée pas de nouvelle frontière dans l'org.
        for (Set<String> roles : java.util.List.of(Set.of("R_SPACE_ADMIN"), Set.<String>of())) {
            PolicyDecision decision = evaluateTmsRoute(subject(roles), TMS_SPACES_PATH, Action.CREATE);
            assertThat(decision.isDeny()).as(roles.toString()).isTrue();
            assertThat(decision.getPolicyId()).isEqualTo("POL_SPACE_CREATE_ORG_AUTHORITY_REQUIRED");
        }
    }

    @Test
    void tmsSpaceDetail_allowedForOrgAuthorityOrLocalSpaceAdmin() {
        assertThat(evaluateTmsRoute(subject(Set.of("R_ORG_ADMIN")),
                TMS_SPACES_PATH + "/" + OTHER_SPACE, Action.READ).isDeny()).isFalse();

        // Un space admin lit le space que son token désigne déjà (pas de 403 absurde).
        assertThat(evaluateTmsRoute(subject(Set.of("R_SPACE_ADMIN")),
                TMS_SPACES_PATH + "/" + SPACE, Action.READ).isDeny()).isFalse();
    }

    @Test
    void tmsSpaceDetail_deniedForSpaceAdminOfAnotherSpace() {
        PolicyDecision decision = evaluateTmsRoute(subject(Set.of("R_SPACE_ADMIN")),
                TMS_SPACES_PATH + "/" + OTHER_SPACE, Action.READ);

        assertThat(decision.isDeny()).isTrue();
        assertThat(decision.getPolicyId()).isEqualTo("POL_SPACE_READ_ORG_OR_LOCAL_ADMIN_REQUIRED");
    }

    @Test
    void tmsSpaceDetail_ordinaryMember_denied() {
        PolicyDecision decision = evaluateTmsRoute(subject(Set.of()),
                TMS_SPACES_PATH + "/" + SPACE, Action.READ);

        assertThat(decision.isDeny()).isTrue();
        assertThat(decision.getPolicyId()).isEqualTo("POL_SPACE_READ_ORG_OR_LOCAL_ADMIN_REQUIRED");
    }

    @Test
    void tmsSpaceCollection_ungovernedActions_failClosed() {
        // UPDATE/DELETE sur la collection ne sont gouvernés pour personne,
        // pas même une autorité ORG.
        for (Action action : new Action[]{Action.UPDATE, Action.DELETE}) {
            PolicyDecision decision = evaluateTmsRoute(subject(Set.of("R_ORG_OWNER")),
                    TMS_SPACES_PATH, action);
            assertThat(decision.isDeny()).as(action.name()).isTrue();
            assertThat(decision.getPolicyId()).isEqualTo("POL_SPACE_ACTION_NOT_SUPPORTED");
        }
    }

    @Test
    void tmsSpaceDetail_ungovernedActions_failClosed_evenForOrgAuthority() {
        String detail = TMS_SPACES_PATH + "/" + SPACE;

        // Tant que le lifecycle n'est pas gouverné, seule la lecture existe sur le
        // détail — un futur UPDATE/DELETE ne doit jamais passer par accident.
        assertThat(evaluateTmsRoute(subject(Set.of("R_ORG_OWNER")), detail, Action.UPDATE).getPolicyId())
                .isEqualTo("POL_SPACE_ACTION_NOT_SUPPORTED");
        assertThat(evaluateTmsRoute(subject(Set.of("R_ORG_ADMIN")), detail, Action.DELETE).getPolicyId())
                .isEqualTo("POL_SPACE_ACTION_NOT_SUPPORTED");
        assertThat(evaluateTmsRoute(subject(Set.of("R_SPACE_ADMIN")), detail, Action.UPDATE).getPolicyId())
                .isEqualTo("POL_SPACE_ACTION_NOT_SUPPORTED");
        assertThat(evaluateTmsRoute(subject(Set.of("R_SPACE_ADMIN")), detail, Action.DELETE).getPolicyId())
                .isEqualTo("POL_SPACE_ACTION_NOT_SUPPORTED");
    }

    @Test
    void tmsSpaceSubRoutes_notGoverned_failClosed() {
        String detail = TMS_SPACES_PATH + "/" + SPACE;

        record Case(String path, Action action) {}
        for (Case c : new Case[]{
                new Case(detail + "/suspend", Action.CREATE),
                new Case(detail + "/disable", Action.CREATE),
                new Case(detail + "/unknown-command", Action.CREATE),
                new Case(detail + "/purge", Action.DELETE)}) {
            PolicyDecision decision = evaluateTmsRoute(subject(Set.of("R_ORG_OWNER")), c.path(), c.action());
            assertThat(decision.isDeny()).as(c.path()).isTrue();
            assertThat(decision.getPolicyId()).as(c.path()).isEqualTo("POL_SPACE_ROUTE_NOT_GOVERNED");
        }
    }

    @Test
    void tmsSpaceSurface_crossOrgPath_deniedEvenForOrgOwner() {
        String foreignOrgPath = "/api/v1/orgs/99999999-0000-0000-0000-000000000009/spaces";
        PolicyDecision decision = evaluateTmsRoute(subject(Set.of("R_ORG_OWNER")), foreignOrgPath, Action.READ);

        assertThat(decision.isDeny()).isTrue();
        assertThat(decision.getPolicyId()).isEqualTo("POL_ORG_MISMATCH");
    }

    @Test
    void tmsSpaceSurface_platformTokenWithoutOrg_denied() {
        Subject platform = new Subject("actor", Set.of("R_PLATFORM_ADMIN"), Set.of(), null, null);
        PolicyDecision decision = evaluateTmsRoute(platform, TMS_SPACES_PATH, Action.READ);

        assertThat(decision.isDeny()).isTrue();
        assertThat(decision.getPolicyId()).isEqualTo("POL_ORG_MISMATCH");
    }

    @Test
    void tmsSpaceRules_ignoreReadableCodeRoutes() {
        // Doctrine d'identification : TMS = UUID, TIS-CORE = codes lisibles.
        // Une route en codes ne doit jamais tomber dans les règles spaces TMS.
        PolicyDecision decision = evaluateTmsRoute(subject(Set.of("R_SPACE_ADMIN")),
                "/api/v1/orgs/takibo-iam/spaces", Action.READ);

        assertThat(decision.isDeny()).isFalse();
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
