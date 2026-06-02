package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.BusinessRoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessRoleAssignmentRepositoryAdapterTest {

    private static final UUID ORG_ID      = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID    = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID IDENTITY_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID ROLE_ID     = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock private JpaRoleAssignmentRepository jpa;

    @InjectMocks
    private BusinessRoleAssignmentRepositoryAdapter adapter;

    @Test
    void existsByOrgIdAndSpaceIdAndIdentityIdAndBusinessRoleId_delegatesWithHumanAndBusinessSource() {
        when(jpa.existsByOrgIdAndSpaceIdAndIdentityTypeAndIdentityIdAndRoleSourceAndBusinessRoleId(
                ORG_ID, SPACE_ID, IdentityType.HUMAN.name(), IDENTITY_ID, RoleSource.BUSINESS, ROLE_ID))
                .thenReturn(true);

        boolean result = adapter.existsByOrgIdAndSpaceIdAndIdentityIdAndBusinessRoleId(
                ORG_ID, SPACE_ID, IDENTITY_ID, ROLE_ID);

        assertThat(result).isTrue();
        verify(jpa).existsByOrgIdAndSpaceIdAndIdentityTypeAndIdentityIdAndRoleSourceAndBusinessRoleId(
                ORG_ID, SPACE_ID, IdentityType.HUMAN.name(), IDENTITY_ID, RoleSource.BUSINESS, ROLE_ID);
    }

    @Test
    void saveAll_mapsToEntityWithCorrectFieldsAndFlushes() {
        BusinessRoleAssignment assignment = new BusinessRoleAssignment(
                ORG_ID, SPACE_ID, IDENTITY_ID, ROLE_ID, Instant.parse("2026-01-01T00:00:00Z"));

        adapter.saveAll(List.of(assignment));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoleAssignmentEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(jpa).saveAllAndFlush(captor.capture());

        RoleAssignmentEntity entity = captor.getValue().get(0);
        assertThat(entity.getOrgId()).isEqualTo(ORG_ID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_ID);
        assertThat(entity.getIdentityType()).isEqualTo(IdentityType.HUMAN.name());
        assertThat(entity.getIdentityId()).isEqualTo(IDENTITY_ID);
        assertThat(entity.getRoleSource()).isEqualTo(RoleSource.BUSINESS);
        assertThat(entity.getBusinessRoleId()).isEqualTo(ROLE_ID);
        assertThat(entity.getRoleCode()).isNull();
    }
}
