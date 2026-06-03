package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.rbac.model.GroupSource;
import com.takibo.identitycore.infrastructure.entity.GroupAssignmentEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.GroupAssignmentMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GovernanceGroupAssignmentRepositoryAdapterTest {

    private static final UUID ORG_ID     = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private JpaGroupAssignmentRepository jpa;
    @Mock private GroupAssignmentMapper mapper;

    @InjectMocks
    private GovernanceGroupAssignmentRepositoryAdapter adapter;

    @Test
    void saveGovernanceAssignment_mapsToEntityFlushesAndReturnsDomain() {
        GroupAssignment domain = technicalAssignment();
        GroupAssignmentEntity entity = entityWithoutId();
        GroupAssignmentEntity saved = entityWithId();
        GroupAssignment result = mock(GroupAssignment.class);

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(result);

        assertThat(adapter.saveGovernanceAssignment(domain)).isSameAs(result);
        verify(jpa).saveAndFlush(entity);
    }

    @Test
    void saveGovernanceAssignment_entityWithoutId_generatesIdBeforePersisting() {
        GroupAssignment domain = technicalAssignment();
        GroupAssignmentEntity entity = entityWithoutId();

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.saveAndFlush(entity)).thenReturn(entityWithId());
        when(mapper.toDomain(any())).thenReturn(mock(GroupAssignment.class));

        adapter.saveGovernanceAssignment(domain);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    void saveGovernanceAssignment_businessSourceRejected() {
        GroupAssignment business = new GroupAssignment(
                null, ORG_ID, SPACE_ID,
                ACCOUNT_ID, new Identity(IdentityType.ACCOUNT, ACCOUNT_ID), IdentityType.ACCOUNT,
                null, GroupSource.BUSINESS, UUID.randomUUID(),
                Instant.now(), "system", null, null
        );

        assertThatThrownBy(() -> adapter.saveGovernanceAssignment(business))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TECHNICAL source");

        verify(jpa, never()).saveAndFlush(any());
    }

    @Test
    void saveGovernanceAssignment_dataIntegrityViolation_throwsDuplicateAssignmentException() {
        GroupAssignment domain = technicalAssignment();
        GroupAssignmentEntity entity = entityWithoutId();

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> adapter.saveGovernanceAssignment(domain))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageContaining("G_SPACE_ADMINS");
    }

    @Test
    void saveGovernanceAssignment_dataIntegrityViolation_withoutSpaceId_messageOmitsSpace() {
        GroupAssignment domain = technicalAssignmentWithoutSpace();
        GroupAssignmentEntity entity = entityWithoutId();

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> adapter.saveGovernanceAssignment(domain))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageNotContaining("and space");
    }

    private GroupAssignment technicalAssignment() {
        return new GroupAssignment(
                null, ORG_ID, SPACE_ID,
                ACCOUNT_ID, new Identity(IdentityType.ACCOUNT, ACCOUNT_ID), IdentityType.ACCOUNT,
                "G_SPACE_ADMINS", GroupSource.TECHNICAL, null,
                Instant.now(), "system", null, null
        );
    }

    private GroupAssignment technicalAssignmentWithoutSpace() {
        return new GroupAssignment(
                null, ORG_ID, null,
                ACCOUNT_ID, new Identity(IdentityType.ACCOUNT, ACCOUNT_ID), IdentityType.ACCOUNT,
                "G_ORG_ADMINS", GroupSource.TECHNICAL, null,
                Instant.now(), "system", null, null
        );
    }

    private GroupAssignmentEntity entityWithoutId() {
        return GroupAssignmentEntity.builder()
                .orgId(ORG_ID).spaceId(SPACE_ID)
                .identityType(IdentityType.ACCOUNT.name())
                .identityId(ACCOUNT_ID)
                .groupCode("G_SPACE_ADMINS")
                .build();
    }

    private GroupAssignmentEntity entityWithId() {
        return GroupAssignmentEntity.builder()
                .id(UUID.randomUUID())
                .orgId(ORG_ID).spaceId(SPACE_ID)
                .identityType(IdentityType.ACCOUNT.name())
                .identityId(ACCOUNT_ID)
                .groupCode("G_SPACE_ADMINS")
                .build();
    }
}
