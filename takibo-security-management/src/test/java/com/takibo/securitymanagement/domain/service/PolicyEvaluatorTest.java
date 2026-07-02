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

    private PolicyDecision evaluateCreateUser(Set<String> roles) {
        return evaluator.evaluate(
                subject(roles),
                new Resource(READABLE_USERS_PATH, ORG, SPACE),
                Action.CREATE,
                new Environment(Instant.now(), "127.0.0.1", 0));
    }

    @Test
    void realTechnicalCodes_areRecognizedAsTenantAdmin() {
        assertThat(evaluateCreateUser(Set.of("R_SPACE_ADMIN")).isDeny()).isFalse();
        assertThat(evaluateCreateUser(Set.of("R_ORG_OWNER")).isDeny()).isFalse();
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
        assertThat(decision.getPolicyId()).isEqualTo("POL_USER_CREATE_ADMIN_REQUIRED");
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
