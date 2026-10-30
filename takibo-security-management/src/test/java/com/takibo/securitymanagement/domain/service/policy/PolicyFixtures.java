package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;
import com.takibo.securitymanagement.support.TokenPermissions;
import org.assertj.core.api.ObjectAssert;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tokens et assertions communs aux tests des stratégies. Les permissions d'un token humain
 * sont celles que ses rôles lui donnent réellement dans sa frontière (résolveur et catalogue
 * réels), comme à l'émission.
 */
final class PolicyFixtures {

    static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";
    static final String OTHER_ORG = "99999999-0000-0000-0000-000000000009";
    static final String SPACE = "bbbbbbbb-0000-0000-0000-000000000002";
    static final String OTHER_SPACE = "cccccccc-0000-0000-0000-000000000003";
    static final String ACCOUNT = "dddddddd-0000-0000-0000-000000000004";
    static final String CLIENT = "eeeeeeee-0000-0000-0000-000000000005";

    /** Chemins hors de toute surface gouvernée par une stratégie de surface. */
    static final String[] UNGOVERNED_PATHS = {"/api/v1/unknown", "/api/v1/orgs/acme/other", "/actuator/health"};

    private PolicyFixtures() {
    }

    /** Token humain ORGANIZATION de l'organisation {@code orgId}. */
    static Subject orgToken(String orgId, String... roles) {
        return human(Set.of(roles), orgId, null, Subjects.SCOPE_ORGANIZATION);
    }

    /** Token humain SPACE situé dans {@code spaceId} de l'organisation {@code orgId}. */
    static Subject spaceToken(String orgId, String spaceId, String... roles) {
        return human(Set.of(roles), orgId, spaceId, Subjects.SCOPE_SPACE);
    }

    /** Token aux permissions données explicitement, indépendamment de tout rôle. */
    static Subject tokenWithPermissions(String scopeLevel, String orgId, String spaceId, String... permissions) {
        return new Subject("actor", Set.of(), Set.of(permissions), orgId, spaceId, "HUMAN", scopeLevel, ACCOUNT);
    }

    static Subject machine(String orgId, String spaceId, String... roles) {
        Set<String> roleSet = Set.of(roles);
        return new Subject("svc", roleSet, permissionsOf(roleSet, orgId, spaceId, Subjects.SCOPE_SPACE),
                orgId, spaceId, "SERVICE", Subjects.SCOPE_SPACE, null);
    }

    static Subject platformToken() {
        return new Subject("actor", Set.of("R_TAKIBO_PLATFORM_ADMIN"),
                permissionsOf(Set.of("R_TAKIBO_PLATFORM_ADMIN"), null, null, "PLATFORM"),
                null, null, "HUMAN", "PLATFORM", null);
    }

    static Resource path(String path) {
        return new Resource(path, null, null);
    }

    static ObjectAssert<PolicyDecision> assertPermit(Optional<PolicyDecision> decision, String policyId) {
        return assertDecision(decision, false, policyId);
    }

    static ObjectAssert<PolicyDecision> assertDeny(Optional<PolicyDecision> decision, String policyId) {
        return assertDecision(decision, true, policyId);
    }

    static ObjectAssert<PolicyDecision> assertDeny(Optional<PolicyDecision> decision, String policyId, String reason) {
        return assertDeny(decision, policyId).satisfies(d -> assertThat(d.getReason()).isEqualTo(reason));
    }

    static void assertSilentOutsideItsSurface(ResourcePolicy policy, Subject subject) {
        for (String outside : UNGOVERNED_PATHS) {
            for (Action action : Action.values()) {
                assertThat(policy.evaluate(subject, path(outside), action))
                        .as("%s %s %s", policy.getClass().getSimpleName(), action, outside)
                        .isEmpty();
            }
        }
    }

    private static ObjectAssert<PolicyDecision> assertDecision(Optional<PolicyDecision> decision, boolean deny,
                                                               String policyId) {
        assertThat(decision).isPresent();
        return assertThat(decision.orElseThrow())
                .satisfies(d -> assertThat(d.isDeny()).as("deny").isEqualTo(deny))
                .satisfies(d -> assertThat(d.getPolicyId()).isEqualTo(policyId));
    }

    private static Subject human(Set<String> roles, String orgId, String spaceId, String scopeLevel) {
        return new Subject("actor", roles, permissionsOf(roles, orgId, spaceId, scopeLevel),
                orgId, spaceId, "HUMAN", scopeLevel, ACCOUNT);
    }

    private static Set<String> permissionsOf(Set<String> roles, String orgId, String spaceId, String scopeLevel) {
        return Set.copyOf(TokenPermissions.of(roles, orgId, spaceId, scopeLevel));
    }
}
