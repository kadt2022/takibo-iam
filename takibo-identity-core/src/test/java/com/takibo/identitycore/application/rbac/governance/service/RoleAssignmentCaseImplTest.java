package com.takibo.identitycore.application.rbac.governance.service;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.exception.InvalidRoleScopeException;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceRoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentCaseImplTest {

    private static final UUID ORG_ID     = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private static final Identity FOUNDER = new Identity(IdentityType.ACCOUNT, ACCOUNT_ID);

    @Mock private GovernanceRoleAssignmentRepository governanceRoleAssignmentRepository;

    @InjectMocks
    private RoleAssignmentCaseImpl service;

    @Test
    void assignTechnicalRole_happyPath_buildsCorrectAssignmentAndDelegates() {
        RoleAssignment saved = mock(RoleAssignment.class);
        when(governanceRoleAssignmentRepository.saveGovernanceAssignment(any())).thenReturn(saved);

        RoleAssignment result = service.assignTechnicalRole(ORG_ID, SPACE_ID, FOUNDER, "R_SPACE_ADMIN", "system");

        assertThat(result).isSameAs(saved);

        ArgumentCaptor<RoleAssignment> captor = ArgumentCaptor.forClass(RoleAssignment.class);
        verify(governanceRoleAssignmentRepository).saveGovernanceAssignment(captor.capture());

        RoleAssignment built = captor.getValue();
        assertThat(built.orgId()).isEqualTo(ORG_ID);
        assertThat(built.spaceId()).isEqualTo(SPACE_ID);
        assertThat(built.identity()).isEqualTo(FOUNDER);
        assertThat(built.roleCode()).isEqualTo("R_SPACE_ADMIN");
        assertThat(built.roleSource()).isEqualTo(RoleSource.TECHNICAL);
        assertThat(built.businessRoleId()).isNull();
        assertThat(built.createdBy()).isEqualTo("system");
    }

    @Test
    void assignTechnicalRole_unknownCode_throwsBeforePersisting() {
        assertThatThrownBy(() ->
                service.assignTechnicalRole(ORG_ID, SPACE_ID, FOUNDER, "UNKNOWN_ROLE", "system"))
                .isInstanceOf(InvalidRoleScopeException.class)
                .hasMessageContaining("Unknown technical role");

        verify(governanceRoleAssignmentRepository, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assignTechnicalRole_systemRoleWithOrgId_throwsScopeViolation() {
        assertThatThrownBy(() ->
                service.assignTechnicalRole(ORG_ID, null, FOUNDER, "R_SYSTEM_ADMIN", "system"))
                .isInstanceOf(InvalidRoleScopeException.class)
                .hasMessageContaining("must not be scoped");

        verify(governanceRoleAssignmentRepository, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assignTechnicalRole_orgRoleWithoutOrgId_throwsScopeViolation() {
        assertThatThrownBy(() ->
                service.assignTechnicalRole(null, null, FOUNDER, "R_ORG_ADMIN", "system"))
                .isInstanceOf(InvalidRoleScopeException.class)
                .hasMessageContaining("requires orgId");

        verify(governanceRoleAssignmentRepository, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assignTechnicalRole_spaceRoleWithoutSpaceId_throwsScopeViolation() {
        assertThatThrownBy(() ->
                service.assignTechnicalRole(ORG_ID, null, FOUNDER, "R_SPACE_ADMIN", "system"))
                .isInstanceOf(InvalidRoleScopeException.class)
                .hasMessageContaining("requires orgId and spaceId");

        verify(governanceRoleAssignmentRepository, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assignTechnicalRole_userRole_happyPath() {
        when(governanceRoleAssignmentRepository.saveGovernanceAssignment(any()))
                .thenReturn(mock(RoleAssignment.class));

        service.assignTechnicalRole(ORG_ID, null, FOUNDER, "R_SELF", "system");

        verify(governanceRoleAssignmentRepository).saveGovernanceAssignment(any());
    }

    @Test
    void assignTechnicalRole_userRoleWithoutOrgId_throwsScopeViolation() {
        assertThatThrownBy(() ->
                service.assignTechnicalRole(null, null, FOUNDER, "R_SELF", "system"))
                .isInstanceOf(InvalidRoleScopeException.class)
                .hasMessageContaining("requires orgId");

        verify(governanceRoleAssignmentRepository, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assignTechnicalRole_duplicate_propagatesDuplicateAssignmentException() {
        when(governanceRoleAssignmentRepository.saveGovernanceAssignment(any()))
                .thenThrow(new DuplicateAssignmentException("already assigned"));

        assertThatThrownBy(() ->
                service.assignTechnicalRole(ORG_ID, SPACE_ID, FOUNDER, "R_SPACE_ADMIN", "system"))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageContaining("already assigned");
    }
}
