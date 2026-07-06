package com.takibo.identitycore.application.rbac.governance.service;

import com.takibo.identitycore.application.rbac.governance.command.AddUserToGroupCommand;
import com.takibo.identitycore.application.rbac.governance.command.RemoveUserFromGroupCommand;
import com.takibo.identitycore.application.rbac.governance.mapper.UserRbacGovernanceMapper;
import com.takibo.identitycore.domain.exception.GroupNotFoundException;
import com.takibo.identitycore.domain.exception.GroupTypeNotAllowedException;
import com.takibo.identitycore.domain.exception.LastAdminRemovalException;
import com.takibo.identitycore.domain.exception.RoleScopeEscalationException;
import com.takibo.identitycore.domain.exception.SelfDemotionException;
import com.takibo.identitycore.domain.exception.UserNotFoundException;
import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.model.GroupNature;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.rbac.model.GroupSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceGroupAssignmentRepository;
import com.takibo.identitycore.domain.rbac.repository.GovernanceRoleAssignmentRepository;
import com.takibo.identitycore.domain.repository.GroupRepository;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserGroupMembershipsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGroupGovernanceServiceTest {

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
    @Mock private GroupRepository groupRepository;
    @Mock private GovernanceGroupAssignmentRepository memberships;
    @Mock private GovernanceRoleAssignmentRepository roleAssignments;

    @Spy private UserRbacGovernanceMapper mapper = new UserRbacGovernanceMapper();

    @InjectMocks
    private UserGroupGovernanceService service;

    private User targetUser() {
        return User.builder()
                .id(UserId.of(USER_ID))
                .orgId(ORG_ID)
                .spaceId(SpaceId.of(SPACE_ID))
                .accountId(AccountId.of(TARGET_ACCOUNT_ID))
                .username("jdoe")
                .build();
    }

    private GroupAssignment membership(String code, GroupSource source) {
        return new GroupAssignment(UUID.randomUUID(), ORG_ID, SPACE_ID,
                TARGET_ACCOUNT_ID, null, null, code, source, null,
                Instant.now(), ACTOR_ACCOUNT_ID.toString(), null, null);
    }

    private void stubActor() {
        when(currentAccountContext.requireCurrentAccountId()).thenReturn(ACTOR_ACCOUNT_ID);
    }

    private void stubTargetUser() {
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.of(targetUser()));
    }

    @Test
    void add_technicalSpaceGroup_persistsMembership() {
        stubActor();
        stubTargetUser();
        when(memberships.existsMembership(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "G_SPACE_ADMINS")).thenReturn(false);
        when(memberships.findDirectMemberships(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID))
                .thenReturn(List.of(membership("G_SPACE_ADMINS", GroupSource.TECHNICAL)));

        UserGroupMembershipsResponse state = service.addToGroup(
                KEY, new AddUserToGroupCommand(USER_ID, "G_SPACE_ADMINS", "joins admins"));

        ArgumentCaptor<GroupAssignment> captor = ArgumentCaptor.forClass(GroupAssignment.class);
        verify(memberships).saveGovernanceAssignment(captor.capture());
        GroupAssignment saved = captor.getValue();
        assertThat(saved.groupCode()).isEqualTo("G_SPACE_ADMINS");
        assertThat(saved.groupSource()).isEqualTo(GroupSource.TECHNICAL);
        assertThat(saved.identityId()).isEqualTo(TARGET_ACCOUNT_ID);
        assertThat(saved.createdBy()).isEqualTo(ACTOR_ACCOUNT_ID.toString());

        assertThat(state.groups()).extracting("code").containsExactly("G_SPACE_ADMINS");
    }

    @Test
    void add_governanceDbGroup_persistsWithGovernanceSource() {
        stubActor();
        stubTargetUser();
        when(groupRepository.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), "GRP_LOCAL"))
                .thenReturn(Optional.of(Group.createNew(
                        SpaceId.of(SPACE_ID), "GRP_LOCAL", "Local", null, GroupNature.GOVERNANCE)));
        when(memberships.existsMembership(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "GRP_LOCAL")).thenReturn(false);
        when(memberships.findDirectMemberships(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID)).thenReturn(List.of());

        service.addToGroup(KEY, new AddUserToGroupCommand(USER_ID, "GRP_LOCAL", null));

        ArgumentCaptor<GroupAssignment> captor = ArgumentCaptor.forClass(GroupAssignment.class);
        verify(memberships).saveGovernanceAssignment(captor.capture());
        assertThat(captor.getValue().groupSource()).isEqualTo(GroupSource.GOVERNANCE);
    }

    @Test
    void add_businessGroup_isForbiddenByGovernancePolicy() {
        stubActor();
        stubTargetUser();
        when(groupRepository.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), "GRP_FINANCE"))
                .thenReturn(Optional.of(Group.createNew(
                        SpaceId.of(SPACE_ID), "GRP_FINANCE", "Finance", null, GroupNature.BUSINESS)));

        assertThatThrownBy(() -> service.addToGroup(KEY, new AddUserToGroupCommand(USER_ID, "GRP_FINANCE", null)))
                .isInstanceOf(GroupTypeNotAllowedException.class);

        verify(memberships, never()).saveGovernanceAssignment(any());
    }

    @Test
    void add_unknownGroup_isNotFound() {
        stubActor();
        stubTargetUser();
        when(groupRepository.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), "NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addToGroup(KEY, new AddUserToGroupCommand(USER_ID, "NOPE", null)))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void add_userOutsideSpace_isNotFound_antiEnumeration() {
        stubActor();
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addToGroup(KEY, new AddUserToGroupCommand(USER_ID, "G_SPACE_ADMINS", null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void add_existingMembership_isIdempotent() {
        stubActor();
        stubTargetUser();
        when(memberships.existsMembership(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "G_SPACE_ADMINS")).thenReturn(true);
        when(memberships.findDirectMemberships(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID))
                .thenReturn(List.of(membership("G_SPACE_ADMINS", GroupSource.TECHNICAL)));

        UserGroupMembershipsResponse state = service.addToGroup(
                KEY, new AddUserToGroupCommand(USER_ID, "G_SPACE_ADMINS", null));

        verify(memberships, never()).saveGovernanceAssignment(any());
        assertThat(state.groups()).hasSize(1);
    }

    @Test
    void add_orgScopedGroup_withoutOrgAuthority_isEscalationDenied() {
        stubActor();
        stubTargetUser();
        when(roleAssignments.findAssignedTechnicalRoleCodes(ORG_ID, SPACE_ID, ACTOR_ACCOUNT_ID))
                .thenReturn(List.of("R_SPACE_ADMIN"));

        assertThatThrownBy(() -> service.addToGroup(KEY, new AddUserToGroupCommand(USER_ID, "G_ORG_ADMINS", null)))
                .isInstanceOf(RoleScopeEscalationException.class);

        verify(memberships, never()).saveGovernanceAssignment(any());
    }

    @Test
    void remove_membership_deletesAndReturnsState() {
        stubActor();
        stubTargetUser();
        when(memberships.existsMembership(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "G_SPACE_USERS")).thenReturn(true);
        when(memberships.findDirectMemberships(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID)).thenReturn(List.of());

        UserGroupMembershipsResponse state = service.removeFromGroup(
                KEY, new RemoveUserFromGroupCommand(USER_ID, "G_SPACE_USERS", null));

        verify(memberships).deleteMembership(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "G_SPACE_USERS");
        assertThat(state.groups()).isEmpty();
    }

    @Test
    void remove_missingMembership_isIdempotent() {
        stubActor();
        stubTargetUser();
        when(memberships.existsMembership(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "G_SPACE_ADMINS")).thenReturn(false);
        when(memberships.findDirectMemberships(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID)).thenReturn(List.of());

        service.removeFromGroup(KEY, new RemoveUserFromGroupCommand(USER_ID, "G_SPACE_ADMINS", null));

        verify(memberships, never()).deleteMembership(any(), any(), any(), any());
    }

    @Test
    void remove_lastSpaceAdminsMember_isDenied() {
        stubActor();
        stubTargetUser();
        when(memberships.existsMembership(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "G_SPACE_ADMINS")).thenReturn(true);
        when(memberships.countIdentitiesInGroup(ORG_ID, SPACE_ID, "G_SPACE_ADMINS")).thenReturn(1L);

        assertThatThrownBy(() -> service.removeFromGroup(
                KEY, new RemoveUserFromGroupCommand(USER_ID, "G_SPACE_ADMINS", null)))
                .isInstanceOf(LastAdminRemovalException.class);

        verify(memberships, never()).deleteMembership(any(), any(), any(), any());
    }

    @Test
    void remove_ownAdminGroup_isSelfDemotionDenied() {
        when(currentAccountContext.requireCurrentAccountId()).thenReturn(TARGET_ACCOUNT_ID);
        stubTargetUser();
        when(memberships.existsMembership(ORG_ID, SPACE_ID, TARGET_ACCOUNT_ID, "G_SPACE_ADMINS")).thenReturn(true);

        assertThatThrownBy(() -> service.removeFromGroup(
                KEY, new RemoveUserFromGroupCommand(USER_ID, "G_SPACE_ADMINS", null)))
                .isInstanceOf(SelfDemotionException.class);
    }
}
