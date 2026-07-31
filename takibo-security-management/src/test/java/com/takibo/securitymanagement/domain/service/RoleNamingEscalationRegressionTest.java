package com.takibo.securitymanagement.domain.service;

import com.takibo.identitycore.application.rbac.effective.model.EffectiveRbac;
import com.takibo.identitycore.application.rbac.effective.service.EffectivePermissionResolver;
import com.takibo.identitycore.application.rbac.effective.service.EffectiveRbacQueryService;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceGroupAssignmentRepository;
import com.takibo.identitycore.domain.rbac.repository.GovernanceRoleAssignmentRepository;
import com.takibo.identitycore.domain.repository.GroupRoleRepository;
import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.Environment;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleNamingEscalationRegressionTest {

    private static final UUID ORG_ID =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private GovernanceRoleAssignmentRepository roleAssignments;
    @Mock private GovernanceGroupAssignmentRepository groupMemberships;
    @Mock private GroupRoleRepository groupRoles;
    @Mock private EffectivePermissionResolver permissionResolver;

    @Test
    void legacyCanonicalGovernanceRoles_doNotEnterTokenOrGrantAdministratorStatus() {
        List<RoleAssignment> legacyAssignments = List.of(
                legacyGovernanceAssignment("R_ORG_OWNER"),
                legacyGovernanceAssignment("R_ORG_ADMIN"),
                legacyGovernanceAssignment("R_SPACE_ADMIN"));
        when(roleAssignments.findDirectAssignments(ORG_ID, SPACE_ID, ACCOUNT_ID))
                .thenReturn(legacyAssignments);
        when(groupMemberships.findDirectMemberships(ORG_ID, SPACE_ID, ACCOUNT_ID))
                .thenReturn(List.of());
        when(permissionResolver.resolve(any())).thenReturn(Set.of());

        EffectiveRbac effective = new EffectiveRbacQueryService(
                roleAssignments, groupMemberships, groupRoles, permissionResolver)
                .effectiveFor(ORG_ID, SPACE_ID, ACCOUNT_ID);

        assertThat(effective.roles()).isEmpty();

        Subject subject = new Subject(
                ACCOUNT_ID.toString(), Set.copyOf(effective.roles()), Set.of(),
                ORG_ID.toString(), SPACE_ID.toString(), "HUMAN", "SPACE",
                ACCOUNT_ID.toString());
        PolicyDecision decision = new PolicyEvaluator().evaluate(
                subject,
                new Resource("/api/v1/orgs/takibo-iam/spaces/finance/users",
                        ORG_ID.toString(), SPACE_ID.toString()),
                Action.CREATE,
                new Environment(Instant.now(), "127.0.0.1", 0));

        assertThat(decision.isDeny()).isTrue();
        assertThat(decision.getPolicyId()).isEqualTo("POL_USER_ADMIN_REQUIRED");
    }

    private RoleAssignment legacyGovernanceAssignment(String code) {
        return new RoleAssignment(
                UUID.randomUUID(), ORG_ID, SPACE_ID, null,
                code, RoleSource.GOVERNANCE, null,
                Instant.now(), "legacy", null, null);
    }
}
