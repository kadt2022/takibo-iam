package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.domain.rbac.model.BusinessRoleAssignment;
import com.takibo.identitycore.domain.repository.BusinessRoleAssignmentRepository;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.repository.TakiboIdentityRepository;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessRoleAssignmentServiceTest {

    private static final UUID ORG_ID      = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID    = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID  = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID IDENTITY_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID ROLE_ID     = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock private TakiboIdentityRepository takiboIdentityRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private BusinessRoleAssignmentRepository businessRoleAssignmentRepository;

    @InjectMocks
    private BusinessRoleAssignmentService service;

    // -----------------------------------------------------------------------
    // Circuit protection : le port business ne retourne que des rôles BUSINESS
    // -----------------------------------------------------------------------

    @Test
    void assignBusinessRoles_governanceCodeNotFoundInBusinessPort_throws() {
        when(takiboIdentityRepository.lockAndFindIdentityIdByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(IDENTITY_ID));
        when(roleRepository.findBusinessRolesByOrgAndSpaceAndCodes(ORG_ID, SPACE_ID, List.of("R_SPACE_ADMIN")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.assignBusinessRoles(ORG_ID, SPACE_ID, ACCOUNT_ID, List.of("R_SPACE_ADMIN")))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("Unknown business role codes");

        verify(businessRoleAssignmentRepository, never()).saveAll(any());
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    void assignBusinessRoles_happyPath_savesBusinessRoleAssignment() {
        when(takiboIdentityRepository.lockAndFindIdentityIdByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(IDENTITY_ID));
        when(roleRepository.findBusinessRolesByOrgAndSpaceAndCodes(ORG_ID, SPACE_ID, List.of("MANAGER")))
                .thenReturn(List.of(managerRole()));
        when(businessRoleAssignmentRepository.existsByOrgIdAndSpaceIdAndIdentityIdAndBusinessRoleId(
                ORG_ID, SPACE_ID, IDENTITY_ID, ROLE_ID))
                .thenReturn(false);

        service.assignBusinessRoles(ORG_ID, SPACE_ID, ACCOUNT_ID, List.of("MANAGER"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessRoleAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(businessRoleAssignmentRepository).saveAll(captor.capture());

        BusinessRoleAssignment assignment = captor.getValue().get(0);
        assertThat(assignment.orgId()).isEqualTo(ORG_ID);
        assertThat(assignment.spaceId()).isEqualTo(SPACE_ID);
        assertThat(assignment.identityId()).isEqualTo(IDENTITY_ID);
        assertThat(assignment.businessRoleId()).isEqualTo(ROLE_ID);
    }

    // -----------------------------------------------------------------------
    // Idempotence
    // -----------------------------------------------------------------------

    @Test
    void assignBusinessRoles_alreadyAssigned_isIdempotent() {
        when(takiboIdentityRepository.lockAndFindIdentityIdByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(IDENTITY_ID));
        when(roleRepository.findBusinessRolesByOrgAndSpaceAndCodes(ORG_ID, SPACE_ID, List.of("MANAGER")))
                .thenReturn(List.of(managerRole()));
        when(businessRoleAssignmentRepository.existsByOrgIdAndSpaceIdAndIdentityIdAndBusinessRoleId(
                ORG_ID, SPACE_ID, IDENTITY_ID, ROLE_ID))
                .thenReturn(true);

        service.assignBusinessRoles(ORG_ID, SPACE_ID, ACCOUNT_ID, List.of("MANAGER"));

        verify(businessRoleAssignmentRepository, never()).saveAll(any());
    }

    // -----------------------------------------------------------------------
    // Cas limites
    // -----------------------------------------------------------------------

    @Test
    void assignBusinessRoles_emptyList_returnsEarlyWithoutAnyQuery() {
        service.assignBusinessRoles(ORG_ID, SPACE_ID, ACCOUNT_ID, List.of());

        verify(takiboIdentityRepository, never()).lockAndFindIdentityIdByOrgIdAndAccountId(any(), any());
        verify(roleRepository, never()).findBusinessRolesByOrgAndSpaceAndCodes(any(), any(), any());
        verify(businessRoleAssignmentRepository, never()).saveAll(any());
    }

    @Test
    void assignBusinessRoles_identityNotFound_throws() {
        when(takiboIdentityRepository.lockAndFindIdentityIdByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignBusinessRoles(ORG_ID, SPACE_ID, ACCOUNT_ID, List.of("MANAGER")))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("no TakiboIdentity found");

        verify(roleRepository, never()).findBusinessRolesByOrgAndSpaceAndCodes(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Role managerRole() {
        return Role.builder()
                .id(RoleId.of(ROLE_ID))
                .spaceId(SpaceId.of(SPACE_ID))
                .code("MANAGER")
                .name("Manager")
                .nature(RoleNature.BUSINESS)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }
}
