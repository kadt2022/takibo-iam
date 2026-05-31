package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.infrastructure.entity.RoleEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleRepository;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRoleRepository;
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

    @Mock private JpaRoleRepository roleRepository;
    @Mock private JpaUserRoleRepository userRoleRepository;
    @Mock private SpaceStatusCheckerCase spaceStatusCheckerCase;
    @Mock private Clock clock;

    @InjectMocks
    private UserRoleAssignmentService service;

    @Test
    void assignRolesToUser_nullList_returnsWithoutQueryingRoles() {
        service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID, null);

        verify(roleRepository, never()).findByOrgIdAndSpaceIdAndCodeIn(any(), any(), any());
        verify(userRoleRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void assignRolesToUser_emptyList_returnsWithoutQueryingRoles() {
        service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID, List.of());

        verify(roleRepository, never()).findByOrgIdAndSpaceIdAndCodeIn(any(), any(), any());
        verify(userRoleRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void assignRolesToUser_unknownRoleCode_throws() {
        when(roleRepository.findByOrgIdAndSpaceIdAndCodeIn(ORG_ID, SPACE_UUID, List.of("UNKNOWN_ROLE")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID, List.of("UNKNOWN_ROLE")))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("UNKNOWN_ROLE");

        verify(userRoleRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void assignRolesToUser_alreadyAssigned_skipsInsert() {
        RoleEntity role = RoleEntity.builder()
                .id(ROLE_ID).orgId(ORG_ID).spaceId(SPACE_UUID).code("VIEWER").name("Viewer").build();

        when(roleRepository.findByOrgIdAndSpaceIdAndCodeIn(ORG_ID, SPACE_UUID, List.of("VIEWER")))
                .thenReturn(List.of(role));
        when(userRoleRepository.existsByOrgIdAndSpaceIdAndUserIdAndRoleId(ORG_ID, SPACE_UUID, USER_UUID, ROLE_ID))
                .thenReturn(true);

        service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID, List.of("VIEWER"));

        verify(userRoleRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void assignRolesToUser_happyPath_savesEntityWithCorrectFields() {
        RoleEntity role = RoleEntity.builder()
                .id(ROLE_ID).orgId(ORG_ID).spaceId(SPACE_UUID).code("EDITOR").name("Editor").build();
        Instant fixedNow = Instant.parse("2026-01-01T00:00:00Z");

        when(roleRepository.findByOrgIdAndSpaceIdAndCodeIn(ORG_ID, SPACE_UUID, List.of("EDITOR")))
                .thenReturn(List.of(role));
        when(userRoleRepository.existsByOrgIdAndSpaceIdAndUserIdAndRoleId(ORG_ID, SPACE_UUID, USER_UUID, ROLE_ID))
                .thenReturn(false);
        when(clock.instant()).thenReturn(fixedNow);

        service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID, List.of("EDITOR"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.takibo.identitycore.infrastructure.entity.UserRoleEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(userRoleRepository).saveAllAndFlush(captor.capture());

        List<com.takibo.identitycore.infrastructure.entity.UserRoleEntity> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        com.takibo.identitycore.infrastructure.entity.UserRoleEntity entity = saved.get(0);
        assertThat(entity.getOrgId()).isEqualTo(ORG_ID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_UUID);
        assertThat(entity.getUserId()).isEqualTo(USER_UUID);
        assertThat(entity.getRoleId()).isEqualTo(ROLE_ID);
        assertThat(entity.getAssignedAt()).isEqualTo(fixedNow);
    }

    @Test
    void assignRolesToUser_duplicateCodesInRequest_deduplicatesBeforeQuerying() {
        RoleEntity role = RoleEntity.builder()
                .id(ROLE_ID).orgId(ORG_ID).spaceId(SPACE_UUID).code("EDITOR").name("Editor").build();
        Instant fixedNow = Instant.parse("2026-01-01T00:00:00Z");

        when(roleRepository.findByOrgIdAndSpaceIdAndCodeIn(ORG_ID, SPACE_UUID, List.of("EDITOR")))
                .thenReturn(List.of(role));
        when(userRoleRepository.existsByOrgIdAndSpaceIdAndUserIdAndRoleId(ORG_ID, SPACE_UUID, USER_UUID, ROLE_ID))
                .thenReturn(false);
        when(clock.instant()).thenReturn(fixedNow);

        service.assignRolesToUser(ORG_ID, SPACE_ID, USER_ID, List.of("EDITOR", "EDITOR", "EDITOR"));

        verify(roleRepository).findByOrgIdAndSpaceIdAndCodeIn(ORG_ID, SPACE_UUID, List.of("EDITOR"));
    }
}
