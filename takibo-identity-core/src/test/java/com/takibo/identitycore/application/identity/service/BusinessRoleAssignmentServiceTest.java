package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import com.takibo.identitycore.infrastructure.entity.RoleEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.RoleJpaAssignmentMapper;
import com.takibo.identitycore.infrastructure.entity.TakiboIdentityEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleAssignmentRepository;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleRepository;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaTakiboIdentityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class BusinessRoleAssignmentServiceTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID IDENTITY_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID ROLE_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock
    private JpaRoleRepository roleRepository;

    @Mock
    private JpaRoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private RoleJpaAssignmentMapper roleAssignmentMapper;

    @Mock
    private JpaTakiboIdentityRepository takiboIdentityRepository;

    @InjectMocks
    private BusinessRoleAssignmentService service;

    @Test
    void assignBusinessRoles_rejectsTechnicalRoleCodes() {
        List<String> roleCodes = List.of("R_SPACE_ADMIN");

        assertThatThrownBy(() -> service.assignBusinessRoles(ORG_ID, SPACE_ID, ACCOUNT_ID, roleCodes))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("Technical role codes cannot be assigned");

        verify(roleRepository, never()).findByOrgIdAndSpaceIdAndCodeIn(any(), any(), any());
        verify(roleAssignmentRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void assignBusinessRoles_createsBusinessRoleAssignment() {
        RoleEntity manager = RoleEntity.builder()
                .id(ROLE_ID)
                .orgId(ORG_ID)
                .spaceId(SPACE_ID)
                .code("MANAGER")
                .name("Manager")
                .build();

        TakiboIdentityEntity identityEntity = TakiboIdentityEntity.builder()
                .identityId(IDENTITY_ID)
                .orgId(ORG_ID)
                .accountId(ACCOUNT_ID)
                .build();
        when(takiboIdentityRepository.lockByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(identityEntity));
        when(roleRepository.findByOrgIdAndSpaceIdAndCodeIn(ORG_ID, SPACE_ID, List.of("MANAGER")))
                .thenReturn(List.of(manager));
        when(roleAssignmentRepository.existsByOrgIdAndSpaceIdAndIdentityTypeAndIdentityIdAndRoleSourceAndBusinessRoleId(
                ORG_ID, SPACE_ID, IdentityType.HUMAN.name(), IDENTITY_ID, RoleSource.BUSINESS, ROLE_ID))
                .thenReturn(false);
        when(roleAssignmentMapper.toEntity(any())).thenAnswer(invocation -> {
            RoleAssignment assignment = invocation.getArgument(0);
            return RoleAssignmentEntity.builder()
                    .orgId(assignment.orgId())
                    .spaceId(assignment.spaceId())
                    .identityType(assignment.identity().type().name())
                    .identityId(assignment.identity().id())
                    .roleCode(assignment.roleCode())
                    .roleSource(assignment.roleSource())
                    .businessRoleId(assignment.businessRoleId())
                    .build();
        });

        service.assignBusinessRoles(ORG_ID, SPACE_ID, ACCOUNT_ID, List.of("MANAGER"));

        ArgumentCaptor<RoleAssignment> assignmentCaptor = ArgumentCaptor.forClass(RoleAssignment.class);
        verify(roleAssignmentMapper).toEntity(assignmentCaptor.capture());

        RoleAssignment assignment = assignmentCaptor.getValue();
        assertThat(assignment.orgId()).isEqualTo(ORG_ID);
        assertThat(assignment.spaceId()).isEqualTo(SPACE_ID);
        assertThat(assignment.identity().type()).isEqualTo(IdentityType.HUMAN);
        assertThat(assignment.identity().id()).isEqualTo(IDENTITY_ID);
        assertThat(assignment.roleSource()).isEqualTo(RoleSource.BUSINESS);
        assertThat(assignment.roleCode()).isNull();
        assertThat(assignment.businessRoleId()).isEqualTo(ROLE_ID);

        verify(roleAssignmentRepository).saveAllAndFlush(any());
    }

    @Test
    void assignBusinessRoles_emptyList_returnsWithoutQueryingRoles() {
        service.assignBusinessRoles(ORG_ID, SPACE_ID, ACCOUNT_ID, List.of());

        verify(roleRepository, never()).findByOrgIdAndSpaceIdAndCodeIn(any(), any(), any());
        verify(roleAssignmentRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void assignBusinessRoles_alreadyAssigned_isIdempotent() {
        RoleEntity manager = RoleEntity.builder()
                .id(ROLE_ID)
                .orgId(ORG_ID)
                .spaceId(SPACE_ID)
                .code("MANAGER")
                .name("Manager")
                .build();

        TakiboIdentityEntity identityEntityForIdempotent = TakiboIdentityEntity.builder()
                .identityId(IDENTITY_ID)
                .orgId(ORG_ID)
                .accountId(ACCOUNT_ID)
                .build();
        when(takiboIdentityRepository.lockByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(identityEntityForIdempotent));
        when(roleRepository.findByOrgIdAndSpaceIdAndCodeIn(ORG_ID, SPACE_ID, List.of("MANAGER")))
                .thenReturn(List.of(manager));
        when(roleAssignmentRepository.existsByOrgIdAndSpaceIdAndIdentityTypeAndIdentityIdAndRoleSourceAndBusinessRoleId(
                ORG_ID, SPACE_ID, IdentityType.HUMAN.name(), IDENTITY_ID, RoleSource.BUSINESS, ROLE_ID))
                .thenReturn(true);

        service.assignBusinessRoles(ORG_ID, SPACE_ID, ACCOUNT_ID, List.of("MANAGER"));

        verify(roleAssignmentMapper, never()).toEntity(any());
        verify(roleAssignmentRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void assignBusinessRoles_identityNotFound_throws() {
        when(takiboIdentityRepository.lockByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignBusinessRoles(ORG_ID, SPACE_ID, ACCOUNT_ID, List.of("MANAGER")))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("identity does not exist");

        verify(roleRepository, never()).findByOrgIdAndSpaceIdAndCodeIn(any(), any(), any());
        verify(roleAssignmentRepository, never()).saveAllAndFlush(any());
    }
}
