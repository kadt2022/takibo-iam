package com.takibo.identitycore.application.rbac.governance.service;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.rbac.model.GroupSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceGroupAssignmentRepository;
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
class GroupAssignmentCaseImplTest {

    private static final UUID ORG_ID     = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private static final Identity FOUNDER = new Identity(IdentityType.ACCOUNT, ACCOUNT_ID);

    @Mock private GovernanceGroupAssignmentRepository governanceGroupAssignmentRepository;

    @InjectMocks
    private GroupAssignmentCaseImpl service;

    @Test
    void assignTechnicalGroup_spaceGroup_happyPath_buildsCorrectAssignmentAndDelegates() {
        GroupAssignment saved = mock(GroupAssignment.class);
        when(governanceGroupAssignmentRepository.saveGovernanceAssignment(any())).thenReturn(saved);

        GroupAssignment result = service.assignTechnicalGroup(
                ORG_ID, SPACE_ID, FOUNDER, "G_SPACE_ADMINS", "system");

        assertThat(result).isSameAs(saved);

        ArgumentCaptor<GroupAssignment> captor = ArgumentCaptor.forClass(GroupAssignment.class);
        verify(governanceGroupAssignmentRepository).saveGovernanceAssignment(captor.capture());

        GroupAssignment built = captor.getValue();
        assertThat(built.orgId()).isEqualTo(ORG_ID);
        assertThat(built.spaceId()).isEqualTo(SPACE_ID);
        assertThat(built.identityId()).isEqualTo(ACCOUNT_ID);
        assertThat(built.groupSource()).isEqualTo(GroupSource.TECHNICAL);
        assertThat(built.businessGroupId()).isNull();
        assertThat(built.createdBy()).isEqualTo("system");
    }

    @Test
    void assignTechnicalGroup_orgGroup_happyPath_doesNotRequireSpaceId() {
        when(governanceGroupAssignmentRepository.saveGovernanceAssignment(any()))
                .thenReturn(mock(GroupAssignment.class));

        service.assignTechnicalGroup(ORG_ID, null, FOUNDER, "G_ORG_ADMINS", "system");

        verify(governanceGroupAssignmentRepository).saveGovernanceAssignment(any());
    }

    @Test
    void assignTechnicalGroup_unknownCode_throwsBeforePersisting() {
        assertThatThrownBy(() ->
                service.assignTechnicalGroup(ORG_ID, SPACE_ID, FOUNDER, "UNKNOWN_GROUP", "system"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown technical group");

        verify(governanceGroupAssignmentRepository, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assignTechnicalGroup_orgGroupWithoutOrgId_throwsScopeViolation() {
        assertThatThrownBy(() ->
                service.assignTechnicalGroup(null, null, FOUNDER, "G_ORG_ADMINS", "system"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires orgId");

        verify(governanceGroupAssignmentRepository, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assignTechnicalGroup_spaceGroupWithoutSpaceId_throwsScopeViolation() {
        assertThatThrownBy(() ->
                service.assignTechnicalGroup(ORG_ID, null, FOUNDER, "G_SPACE_ADMINS", "system"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires orgId and spaceId");

        verify(governanceGroupAssignmentRepository, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assignTechnicalGroup_spaceGroupWithoutOrgId_throwsScopeViolation() {
        assertThatThrownBy(() ->
                service.assignTechnicalGroup(null, SPACE_ID, FOUNDER, "G_SPACE_ADMINS", "system"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires orgId and spaceId");

        verify(governanceGroupAssignmentRepository, never()).saveGovernanceAssignment(any());
    }

    @Test
    void assignTechnicalGroup_duplicate_propagatesDuplicateAssignmentException() {
        when(governanceGroupAssignmentRepository.saveGovernanceAssignment(any()))
                .thenThrow(new DuplicateAssignmentException("already assigned"));

        assertThatThrownBy(() ->
                service.assignTechnicalGroup(ORG_ID, SPACE_ID, FOUNDER, "G_SPACE_ADMINS", "system"))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageContaining("already assigned");
    }
}
