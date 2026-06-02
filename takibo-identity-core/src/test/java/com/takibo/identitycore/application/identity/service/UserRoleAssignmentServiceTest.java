package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.domain.rbac.model.UserGovernanceRoleAssignment;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.repository.UserGovernanceRoleRepository;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRoleAssignmentServiceTest {

    private static final UUID ORG_ID     = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_UUID  = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID ROLE_ID    = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private static final SpaceId SPACE_ID = SpaceId.of(SPACE_UUID);
    private static final UserId  USER_ID  = UserId.of(USER_UUID);

    @Mock private RoleRepository roleRepository;
    @Mock private UserGovernanceRoleRepository userGovernanceRoleRepository;
    @Mock private SpaceStatusCheckerCase spaceStatusCheckerCase;
    @Mock private Clock clock;

    @InjectMocks
    private UserRoleAssignmentService service;

    // -----------------------------------------------------------------------
    // Circuit protection : le port governance ne retourne que des rôles GOVERNANCE
    // -----------------------------------------------------------------------

    @Test
    void assignRolesToUser_businessCodeNotFoundInGovernancePort_throws() {
        when(roleRepository.findGovernanceRolesByOrgAndSpaceAndCodes(ORG_ID, SPACE_UUID, List.of("MANAGER")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID, List.of("MANAGER")))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("Unknown governance role codes");

        verify(userGovernanceRoleRepository, never()).saveAll(any());
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    void assignRolesToUser_happyPath_savesGovernanceRoleAssignment() {
        Instant fixedNow = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(fixedNow);
        when(roleRepository.findGovernanceRolesByOrgAndSpaceAndCodes(ORG_ID, SPACE_UUID, List.of("R_SPACE_ADMIN")))
                .thenReturn(List.of(spaceAdminRole()));
        when(userGovernanceRoleRepository.existsByOrgIdAndSpaceIdAndUserIdAndGovernanceRoleId(
                ORG_ID, SPACE_UUID, USER_UUID, ROLE_ID))
                .thenReturn(false);

        service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID, List.of("R_SPACE_ADMIN"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserGovernanceRoleAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(userGovernanceRoleRepository).saveAll(captor.capture());

        UserGovernanceRoleAssignment assignment = captor.getValue().get(0);
        assertThat(assignment.orgId()).isEqualTo(ORG_ID);
        assertThat(assignment.spaceId()).isEqualTo(SPACE_UUID);
        assertThat(assignment.userId()).isEqualTo(USER_UUID);
        assertThat(assignment.governanceRoleId()).isEqualTo(ROLE_ID);
        assertThat(assignment.assignedAt()).isEqualTo(fixedNow);
    }

    // -----------------------------------------------------------------------
    // Idempotence
    // -----------------------------------------------------------------------

    @Test
    void assignRolesToUser_alreadyAssigned_isIdempotent() {
        when(roleRepository.findGovernanceRolesByOrgAndSpaceAndCodes(ORG_ID, SPACE_UUID, List.of("R_SPACE_ADMIN")))
                .thenReturn(List.of(spaceAdminRole()));
        when(userGovernanceRoleRepository.existsByOrgIdAndSpaceIdAndUserIdAndGovernanceRoleId(
                ORG_ID, SPACE_UUID, USER_UUID, ROLE_ID))
                .thenReturn(true);

        service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID, List.of("R_SPACE_ADMIN"));

        verify(userGovernanceRoleRepository, never()).saveAll(any());
    }

    // -----------------------------------------------------------------------
    // Cas limites
    // -----------------------------------------------------------------------

    @Test
    void assignRolesToUser_nullList_returnsEarlyWithoutQuery() {
        service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID, null);

        verify(roleRepository, never()).findGovernanceRolesByOrgAndSpaceAndCodes(any(), any(), any());
        verify(userGovernanceRoleRepository, never()).saveAll(any());
    }

    @Test
    void assignRolesToUser_emptyList_returnsEarlyWithoutQuery() {
        service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID, List.of());

        verify(roleRepository, never()).findGovernanceRolesByOrgAndSpaceAndCodes(any(), any(), any());
        verify(userGovernanceRoleRepository, never()).saveAll(any());
    }

    @Test
    void assignRolesToUser_duplicateCodesInRequest_deduplicatesBeforeQuerying() {
        Instant fixedNow = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(fixedNow);
        when(roleRepository.findGovernanceRolesByOrgAndSpaceAndCodes(ORG_ID, SPACE_UUID, List.of("R_SPACE_ADMIN")))
                .thenReturn(List.of(spaceAdminRole()));
        when(userGovernanceRoleRepository.existsByOrgIdAndSpaceIdAndUserIdAndGovernanceRoleId(
                ORG_ID, SPACE_UUID, USER_UUID, ROLE_ID))
                .thenReturn(false);

        service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID,
                List.of("R_SPACE_ADMIN", "R_SPACE_ADMIN", "R_SPACE_ADMIN"));

        verify(roleRepository).findGovernanceRolesByOrgAndSpaceAndCodes(ORG_ID, SPACE_UUID, List.of("R_SPACE_ADMIN"));
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Role spaceAdminRole() {
        return Role.builder()
                .id(RoleId.of(ROLE_ID))
                .spaceId(SPACE_ID)
                .code("R_SPACE_ADMIN")
                .name("Space Admin")
                .nature(RoleNature.GOVERNANCE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }
}
