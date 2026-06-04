package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.model.GroupNature;
import com.takibo.identitycore.domain.model.GroupRole;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.domain.repository.GroupRepository;
import com.takibo.identitycore.domain.repository.GroupRoleRepository;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupRoleApplicationServiceTest {

    private static final UUID SPACE_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID GROUP_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ROLE_UUID  = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private GroupRoleRepository groupRoleRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private RoleRepository roleRepository;

    @InjectMocks
    private GroupRoleApplicationService service;

    @Test
    void ensureGroupHasRole_businessGroupAndBusinessRole_delegates() {
        when(groupRepository.findById(GroupId.of(GROUP_UUID)))
                .thenReturn(Optional.of(group(GroupNature.BUSINESS)));
        when(roleRepository.findById(RoleId.of(ROLE_UUID)))
                .thenReturn(Optional.of(role(RoleNature.BUSINESS)));

        service.ensureGroupHasRole(SPACE_UUID, GROUP_UUID, ROLE_UUID);

        ArgumentCaptor<GroupRole> captor = ArgumentCaptor.forClass(GroupRole.class);
        verify(groupRoleRepository).save(captor.capture());

        GroupRole saved = captor.getValue();
        assertThat(saved.getSpaceId().value()).isEqualTo(SPACE_UUID);
        assertThat(saved.getGroupId().value()).isEqualTo(GROUP_UUID);
        assertThat(saved.getRoleId().value()).isEqualTo(ROLE_UUID);
    }

    @Test
    void ensureGroupHasRole_governanceGroupAndGovernanceRole_delegates() {
        when(groupRepository.findById(GroupId.of(GROUP_UUID)))
                .thenReturn(Optional.of(group(GroupNature.GOVERNANCE)));
        when(roleRepository.findById(RoleId.of(ROLE_UUID)))
                .thenReturn(Optional.of(role(RoleNature.GOVERNANCE)));

        service.ensureGroupHasRole(SPACE_UUID, GROUP_UUID, ROLE_UUID);

        verify(groupRoleRepository).save(org.mockito.ArgumentMatchers.any(GroupRole.class));
    }

    @Test
    void ensureGroupHasRole_businessGroupWithGovernanceRole_throws() {
        when(groupRepository.findById(GroupId.of(GROUP_UUID)))
                .thenReturn(Optional.of(group(GroupNature.BUSINESS)));
        when(roleRepository.findById(RoleId.of(ROLE_UUID)))
                .thenReturn(Optional.of(role(RoleNature.GOVERNANCE)));

        assertThatThrownBy(() -> service.ensureGroupHasRole(SPACE_UUID, GROUP_UUID, ROLE_UUID))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("Group and role nature mismatch");

        verify(groupRoleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ensureGroupHasRole_governanceGroupWithBusinessRole_throws() {
        when(groupRepository.findById(GroupId.of(GROUP_UUID)))
                .thenReturn(Optional.of(group(GroupNature.GOVERNANCE)));
        when(roleRepository.findById(RoleId.of(ROLE_UUID)))
                .thenReturn(Optional.of(role(RoleNature.BUSINESS)));

        assertThatThrownBy(() -> service.ensureGroupHasRole(SPACE_UUID, GROUP_UUID, ROLE_UUID))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("Group and role nature mismatch");

        verify(groupRoleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ensureGroupHasRole_groupNotFound_throws() {
        when(groupRepository.findById(GroupId.of(GROUP_UUID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ensureGroupHasRole(SPACE_UUID, GROUP_UUID, ROLE_UUID))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("Group not found");

        verify(groupRoleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ensureGroupHasRole_roleNotFound_throws() {
        when(groupRepository.findById(GroupId.of(GROUP_UUID)))
                .thenReturn(Optional.of(group(GroupNature.BUSINESS)));
        when(roleRepository.findById(RoleId.of(ROLE_UUID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ensureGroupHasRole(SPACE_UUID, GROUP_UUID, ROLE_UUID))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("Role not found");

        verify(groupRoleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Group group(GroupNature nature) {
        return Group.builder()
                .id(GroupId.of(GROUP_UUID))
                .spaceId(SpaceId.of(SPACE_UUID))
                .nature(nature)
                .code("GRP_TEST").name("Test Group")
                .createdAt(Instant.now()).updatedAt(Instant.now()).version(0L)
                .build();
    }

    private Role role(RoleNature nature) {
        return Role.builder()
                .id(RoleId.of(ROLE_UUID))
                .spaceId(SpaceId.of(SPACE_UUID))
                .nature(nature)
                .code("R_TEST").name("Test Role")
                .createdAt(Instant.now()).updatedAt(Instant.now()).version(0L)
                .build();
    }
}
