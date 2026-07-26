package com.takibo.identitycore.application.rbac.governance.service;

import com.takibo.identitycore.application.rbac.governance.command.AssignUserRoleCommand;
import com.takibo.identitycore.application.rbac.governance.command.RemoveUserRoleCommand;
import com.takibo.identitycore.application.rbac.governance.mapper.UserRbacGovernanceMapper;
import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.exception.LastAdminRemovalException;
import com.takibo.identitycore.domain.exception.RoleNotFoundException;
import com.takibo.identitycore.domain.exception.RoleScopeEscalationException;
import com.takibo.identitycore.domain.exception.RoleTypeNotAllowedException;
import com.takibo.identitycore.domain.exception.ReservedTenantRoleCodeException;
import com.takibo.identitycore.domain.exception.SelfDemotionException;
import com.takibo.identitycore.domain.exception.SpaceNotActiveException;
import com.takibo.identitycore.domain.exception.UserNotFoundException;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceRoleAssignmentRepository;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserRoleAssignmentsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleGovernanceServiceTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID TARGET_ACCOUNT_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID ACTOR_ACCOUNT_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final ResolvedSpaceKey KEY =
            new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock private SpaceContextVerifier spaceContextVerifier;
    @Mock private SpaceBoundaryGuard spaceBoundaryGuard;
    @Mock private CurrentAccountContextCase currentAccountContext;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private GovernanceRoleAssignmentRepository assignments;

    @Spy private UserRbacGovernanceMapper mapper = new UserRbacGovernanceMapper();

    @InjectMocks
    private UserRoleGovernanceService service;

    private User targetUser() {
        return User.builder()
                .id(UserId.of(USER_ID))
                .orgId(ORG_ID)
                .spaceId(SpaceId.of(SPACE_ID))
                .accountId(AccountId.of(TARGET_ACCOUNT_ID))
                .username("jdoe")
                .build();
    }

    private Role dbRole(String code, RoleNature nature) {
        Instant now = Instant.now();
        return Role.builder()
                .id(RoleId.generate())
                .spaceId(SpaceId.of(SPACE_ID))
                .code(code).name(code).description("db role")
                .nature(nature)
                .createdAt(now).updatedAt(now).version(0L)
                .build();
    }

    private RoleAssignment directAssignment(String code, RoleSource source) {
        return new RoleAssignment(UUID.randomUUID(), ORG_ID, SPACE_ID, null,
                code, source, null, Instant.now(), ACTOR_ACCOUNT_ID.toString(), null, null);
    }

    private void stubActor() {
        when(currentAccountContext.requireCurrentAccountId()).thenReturn(ACTOR_ACCOUNT_ID);
    }

    private void stubTargetUser() {
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.of(targetUser()));
    }

    // ─────────────────────────── assign ───────────────────────────

    @Test
    void assign_technicalSpaceRole_persistsDirectAssignment() {
        stubActor();
        stubTargetUser();
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_SPACE_USER_ADMIN"))
                .thenReturn(false);
        when(assignments.findDirectAssignments(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID))
                .thenReturn(List.of(directAssignment("R_SPACE_USER_ADMIN", RoleSource.TECHNICAL)));

        UserRoleAssignmentsResponse state = service.assignRole(
                KEY, new AssignUserRoleCommand(USER_ID, "R_SPACE_USER_ADMIN", "delegation"));

        ArgumentCaptor<RoleAssignment> captor = ArgumentCaptor.forClass(RoleAssignment.class);
        verify(assignments).saveGovernanceAssignment(captor.capture());
        RoleAssignment saved = captor.getValue();
        assertThat(saved.roleCode()).isEqualTo("R_SPACE_USER_ADMIN");
        assertThat(saved.roleSource()).isEqualTo(RoleSource.TECHNICAL);
        assertThat(saved.spaceId()).isEqualTo(SPACE_ID);
        assertThat(saved.createdBy()).isEqualTo(ACTOR_ACCOUNT_ID.toString());

        assertThat(state.userId()).isEqualTo(USER_ID);
        assertThat(state.roles()).extracting("code").containsExactly("R_SPACE_USER_ADMIN");
        verify(spaceContextVerifier).validateSpaceContext(SPACE_ID);
        verify(spaceBoundaryGuard).assertTokenMatches(KEY);
    }

    @Test
    void assign_governanceDbRole_persistsWithGovernanceSource() {
        stubActor();
        stubTargetUser();
        when(roleRepository.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), "GOV_LOCAL"))
                .thenReturn(Optional.of(dbRole("GOV_LOCAL", RoleNature.GOVERNANCE)));
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "GOV_LOCAL")).thenReturn(false);
        when(assignments.findDirectAssignments(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID)).thenReturn(List.of());

        service.assignRole(KEY, new AssignUserRoleCommand(USER_ID, "GOV_LOCAL", null));

        ArgumentCaptor<RoleAssignment> captor = ArgumentCaptor.forClass(RoleAssignment.class);
        verify(assignments).saveGovernanceAssignment(captor.capture());
        assertThat(captor.getValue().roleSource()).isEqualTo(RoleSource.GOVERNANCE);
    }

    @Test
    void assign_businessRole_isForbiddenByGovernancePolicy() {
        stubActor();
        stubTargetUser();
        when(roleRepository.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), "B_APPROVER"))
                .thenReturn(Optional.of(dbRole("B_APPROVER", RoleNature.BUSINESS)));

        assertThatThrownBy(() -> service.assignRole(KEY, new AssignUserRoleCommand(USER_ID, "B_APPROVER", null)))
                .isInstanceOf(RoleTypeNotAllowedException.class);

        verify(assignments, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assign_platformRole_doesNotExistForTenants() {
        stubActor();
        stubTargetUser();

        assertThatThrownBy(() -> service.assignRole(
                KEY, new AssignUserRoleCommand(USER_ID, "R_TAKIBO_PLATFORM_ADMIN", null)))
                .isInstanceOf(RoleNotFoundException.class);

        verifyNoInteractions(roleRepository);
        verify(assignments, never()).saveGovernanceAssignment(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PLATFORM_ADMIN", "R_PLATFORM_ADMIN", "ROLE_PLATFORM_ADMIN",
            "R_TAKIBO_CUSTOM", "R_ORG_CUSTOM", "R_SPACE_CUSTOM"
    })
    void assign_legacyTenantRoleWithReservedCode_isRejected(String code) {
        stubActor();
        stubTargetUser();
        when(roleRepository.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), code))
                .thenReturn(Optional.of(dbRole(code, RoleNature.GOVERNANCE)));
        AssignUserRoleCommand command = new AssignUserRoleCommand(USER_ID, code, null);

        assertThatThrownBy(() -> service.assignRole(KEY, command))
                .isInstanceOf(ReservedTenantRoleCodeException.class)
                .hasMessageContaining(code);

        verify(assignments, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assign_selfRole_doesNotExistOnThisSurface() {
        stubActor();
        stubTargetUser();

        assertThatThrownBy(() -> service.assignRole(KEY, new AssignUserRoleCommand(USER_ID, "R_SELF", null)))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void assign_nonAssignableOrganizationOwner_doesNotExistOnThisSurface() {
        stubActor();
        stubTargetUser();

        assertThatThrownBy(() -> service.assignRole(
                KEY, new AssignUserRoleCommand(USER_ID, "R_ORG_OWNER", null)))
                .isInstanceOf(RoleNotFoundException.class)
                .hasMessageContaining("R_ORG_OWNER");

        verify(assignments, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assign_userOutsideSpace_isNotFound_antiEnumeration() {
        stubActor();
        User foreignUser = User.builder()
                .id(UserId.of(USER_ID))
                .orgId(ORG_ID)
                .spaceId(SpaceId.of(UUID.fromString("99999999-0000-0000-0000-000000000009")))
                .accountId(AccountId.of(TARGET_ACCOUNT_ID))
                .username("jdoe")
                .build();
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.of(foreignUser));

        assertThatThrownBy(() -> service.assignRole(KEY, new AssignUserRoleCommand(USER_ID, "R_SPACE_ADMIN", null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void assign_alreadyAssigned_isIdempotent_returnsCurrentState() {
        stubActor();
        stubTargetUser();
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_SPACE_ADMIN")).thenReturn(true);
        when(assignments.findDirectAssignments(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID))
                .thenReturn(List.of(directAssignment("R_SPACE_ADMIN", RoleSource.TECHNICAL)));

        UserRoleAssignmentsResponse state = service.assignRole(
                KEY, new AssignUserRoleCommand(USER_ID, "R_SPACE_ADMIN", null));

        verify(assignments, never()).saveGovernanceAssignment(any());
        assertThat(state.roles()).extracting("code").containsExactly("R_SPACE_ADMIN");
    }

    @Test
    void assign_orgScopedRole_withoutOrgAuthority_isEscalationDenied() {
        stubActor();
        stubTargetUser();
        // L'acteur n'est que space admin : le pouvoir ORGANIZATION est au-dessus de son scope.
        when(assignments.findAssignedTechnicalRoleCodes(ORG_ID, SPACE_ID, ACTOR_ACCOUNT_ID))
                .thenReturn(List.of("R_SPACE_ADMIN"));

        assertThatThrownBy(() -> service.assignRole(KEY, new AssignUserRoleCommand(USER_ID, "R_ORG_ADMIN", null)))
                .isInstanceOf(RoleScopeEscalationException.class);

        verify(assignments, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assign_orgScopedRole_withOrgAuthority_succeeds() {
        stubActor();
        stubTargetUser();
        when(assignments.findAssignedTechnicalRoleCodes(ORG_ID, SPACE_ID, ACTOR_ACCOUNT_ID))
                .thenReturn(List.of("R_ORG_OWNER", "R_SPACE_ADMIN"));
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_ORG_ADMIN")).thenReturn(false);
        when(assignments.findDirectAssignments(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID)).thenReturn(List.of());

        service.assignRole(KEY, new AssignUserRoleCommand(USER_ID, "R_ORG_ADMIN", null));

        verify(assignments).saveGovernanceAssignment(any());
    }

    @Test
    void assign_orgScopedRole_withOrgAuthority_persistsOrgLevelAssignment() {
        stubActor();
        stubTargetUser();
        when(assignments.findAssignedTechnicalRoleCodes(ORG_ID, SPACE_ID, ACTOR_ACCOUNT_ID))
                .thenReturn(List.of("R_ORG_OWNER"));
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_ORG_ADMIN")).thenReturn(false);
        when(assignments.findDirectAssignments(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID)).thenReturn(List.of());

        service.assignRole(KEY, new AssignUserRoleCommand(USER_ID, "R_ORG_ADMIN", "org delegation"));

        ArgumentCaptor<RoleAssignment> captor = ArgumentCaptor.forClass(RoleAssignment.class);
        verify(assignments).saveGovernanceAssignment(captor.capture());
        assertThat(captor.getValue().spaceId()).isNull();
    }

    @Test
    void assign_concurrentDuplicate_isIdempotent() {
        stubActor();
        stubTargetUser();
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_SPACE_ADMIN")).thenReturn(false);
        when(assignments.saveGovernanceAssignment(any()))
                .thenThrow(new DuplicateAssignmentException("already assigned"));
        when(assignments.findDirectAssignments(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID))
                .thenReturn(List.of(directAssignment("R_SPACE_ADMIN", RoleSource.TECHNICAL)));

        UserRoleAssignmentsResponse state = service.assignRole(
                KEY, new AssignUserRoleCommand(USER_ID, "R_SPACE_ADMIN", null));

        assertThat(state.roles()).extracting("code").containsExactly("R_SPACE_ADMIN");
    }

    // ─────────────────────────── remove ───────────────────────────

    @Test
    void remove_directRole_deletesAndReturnsState() {
        stubActor();
        stubTargetUser();
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_SPACE_USER_ADMIN"))
                .thenReturn(true);
        when(assignments.findDirectAssignments(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID)).thenReturn(List.of());

        UserRoleAssignmentsResponse state = service.removeRole(
                KEY, new RemoveUserRoleCommand(USER_ID, "R_SPACE_USER_ADMIN", "offboarding"));

        verify(assignments).deleteAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_SPACE_USER_ADMIN");
        assertThat(state.roles()).isEmpty();
    }

    @Test
    void remove_legacyReservedGovernanceRole_remainsPossibleForCleanup() {
        stubActor();
        stubTargetUser();
        when(roleRepository.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), "PLATFORM_ADMIN"))
                .thenReturn(Optional.of(dbRole("PLATFORM_ADMIN", RoleNature.GOVERNANCE)));
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "PLATFORM_ADMIN"))
                .thenReturn(true);
        when(assignments.findDirectAssignments(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID))
                .thenReturn(List.of());

        service.removeRole(KEY, new RemoveUserRoleCommand(USER_ID, "PLATFORM_ADMIN", "security cleanup"));

        verify(assignments).deleteAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "PLATFORM_ADMIN");
    }

    @Test
    void remove_missingAssignment_isIdempotent_skipsProtections() {
        stubActor();
        stubTargetUser();
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_SPACE_ADMIN")).thenReturn(false);
        when(assignments.findDirectAssignments(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID)).thenReturn(List.of());

        service.removeRole(KEY, new RemoveUserRoleCommand(USER_ID, "R_SPACE_ADMIN", null));

        verify(assignments, never()).deleteAssignment(any(), any(), any(), any());
        verify(assignments, never()).countIdentitiesHoldingRole(any(), any(), any());
    }

    @Test
    void remove_lastSpaceAdmin_isDenied() {
        stubActor();
        stubTargetUser();
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_SPACE_ADMIN")).thenReturn(true);
        when(assignments.countIdentitiesHoldingRole(ORG_ID, SPACE_ID, "R_SPACE_ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.removeRole(KEY, new RemoveUserRoleCommand(USER_ID, "R_SPACE_ADMIN", null)))
                .isInstanceOf(LastAdminRemovalException.class);

        verify(assignments, never()).deleteAssignment(any(), any(), any(), any());
    }

    @Test
    void remove_ownAdminRole_isSelfDemotionDenied_evenWithOtherAdmins() {
        // L'acteur EST le user cible (même account).
        when(currentAccountContext.requireCurrentAccountId()).thenReturn(TARGET_ACCOUNT_ID);
        stubTargetUser();
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_SPACE_ADMIN")).thenReturn(true);

        assertThatThrownBy(() -> service.removeRole(KEY, new RemoveUserRoleCommand(USER_ID, "R_SPACE_ADMIN", null)))
                .isInstanceOf(SelfDemotionException.class);

        verify(assignments, never()).deleteAssignment(any(), any(), any(), any());
    }

    @Test
    void remove_orgScopedRole_withoutOrgAuthority_isEscalationDenied() {
        stubActor();
        stubTargetUser();
        when(assignments.findAssignedTechnicalRoleCodes(ORG_ID, SPACE_ID, ACTOR_ACCOUNT_ID))
                .thenReturn(List.of("R_SPACE_ADMIN"));

        assertThatThrownBy(() -> service.removeRole(KEY, new RemoveUserRoleCommand(USER_ID, "R_ORG_OWNER", null)))
                .isInstanceOf(RoleScopeEscalationException.class);
    }

    @Test
    void remove_orgScopedRole_withOrgAuthority_deletesOrgLevelAssignment() {
        stubActor();
        stubTargetUser();
        when(assignments.findAssignedTechnicalRoleCodes(ORG_ID, SPACE_ID, ACTOR_ACCOUNT_ID))
                .thenReturn(List.of("R_ORG_OWNER"));
        when(assignments.existsAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_ORG_ADMIN")).thenReturn(true);
        when(assignments.findDirectAssignments(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID)).thenReturn(List.of());

        service.removeRole(KEY, new RemoveUserRoleCommand(USER_ID, "R_ORG_ADMIN", "cleanup"));

        verify(assignments).deleteOrgLevelAssignment(ORG_ID, TARGET_ACCOUNT_ID, "R_ORG_ADMIN");
        verify(assignments, never()).deleteAssignment(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "R_ORG_ADMIN");
    }

    // ─────────────────────────── frontière ───────────────────────────

    @Test
    void spaceInactive_denied_beforeAnyWork() {
        doThrow(new SpaceNotActiveException(SPACE_ID))
                .when(spaceContextVerifier).validateSpaceContext(SPACE_ID);

        assertThatThrownBy(() -> service.listDirectRoles(KEY, USER_ID))
                .isInstanceOf(SpaceNotActiveException.class);

        verifyNoInteractions(userRepository, assignments);
    }

    @Test
    void tokenOutsideBoundary_denied_beforeAnyWork() {
        doThrow(new AccessDeniedException("SPACE_CONTEXT_MISMATCH"))
                .when(spaceBoundaryGuard).assertTokenMatches(KEY);

        assertThatThrownBy(() -> service.assignRole(KEY, new AssignUserRoleCommand(USER_ID, "R_SPACE_ADMIN", null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userRepository, assignments);
    }

    @Test
    void machineToken_withoutAccount_cannotGovern() {
        // Token PLATFORM/client_credentials : pas d'account -> la surface est fermée.
        when(currentAccountContext.requireCurrentAccountId())
                .thenThrow(new AccessDeniedException("ACCOUNT_CONTEXT_REQUIRED"));

        assertThatThrownBy(() -> service.assignRole(KEY, new AssignUserRoleCommand(USER_ID, "R_SPACE_ADMIN", null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACCOUNT_CONTEXT_REQUIRED");

        verifyNoInteractions(userRepository, assignments);
    }

    @Test
    void listDirectRoles_returnsSortedDistinctState() {
        stubTargetUser();
        when(assignments.findDirectAssignments(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID)).thenReturn(List.of(
                directAssignment("R_SPACE_ADMIN", RoleSource.TECHNICAL),
                directAssignment("GOV_LOCAL", RoleSource.GOVERNANCE),
                directAssignment("R_SPACE_ADMIN", RoleSource.TECHNICAL)));

        UserRoleAssignmentsResponse state = service.listDirectRoles(KEY, USER_ID);

        assertThat(state.roles()).extracting("code").containsExactly("GOV_LOCAL", "R_SPACE_ADMIN");
        assertThat(state.roles()).allMatch(role -> "DIRECT".equals(role.source()));
    }
}
