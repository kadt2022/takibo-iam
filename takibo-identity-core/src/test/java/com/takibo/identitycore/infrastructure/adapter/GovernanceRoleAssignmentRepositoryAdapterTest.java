package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.RoleJpaAssignmentMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleAssignmentRepository;
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
class GovernanceRoleAssignmentRepositoryAdapterTest {

    private static final UUID ORG_ID    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID  = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private JpaRoleAssignmentRepository jpa;
    @Mock private RoleJpaAssignmentMapper mapper;

    @InjectMocks
    private GovernanceRoleAssignmentRepositoryAdapter adapter;

    @Test
    void save_mapsToEntitySavesAndReturnsDomain() {
        RoleAssignment domain = assignment(null);
        RoleAssignmentEntity entity = entityWithoutId();
        RoleAssignmentEntity saved = entityWithId();
        RoleAssignment result = mock(RoleAssignment.class);

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.save(entity)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(result);

        assertThat(adapter.save(domain)).isSameAs(result);
        verify(jpa).save(entity);
    }

    @Test
    void save_entityWithoutId_generatesIdBeforePersisting() {
        RoleAssignment domain = assignment(null);
        RoleAssignmentEntity entity = entityWithoutId();
        RoleAssignmentEntity saved = entityWithId();

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.save(entity)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(mock(RoleAssignment.class));

        adapter.save(domain);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    void save_dataIntegrityViolation_throwsDuplicateAssignmentException() {
        RoleAssignment domain = assignment(null);
        RoleAssignmentEntity entity = entityWithoutId();

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> adapter.save(domain))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageContaining("R_SPACE_ADMIN");
    }

    private RoleAssignment assignment(UUID id) {
        return new RoleAssignment(
                id, ORG_ID, SPACE_ID,
                new Identity(IdentityType.ACCOUNT, ACCOUNT_ID),
                "R_SPACE_ADMIN", RoleSource.TECHNICAL, null,
                Instant.now(), "system", null, null
        );
    }

    private RoleAssignmentEntity entityWithoutId() {
        return RoleAssignmentEntity.builder()
                .orgId(ORG_ID).spaceId(SPACE_ID)
                .identityType(IdentityType.ACCOUNT.name())
                .identityId(ACCOUNT_ID)
                .roleCode("R_SPACE_ADMIN")
                .build();
    }

    private RoleAssignmentEntity entityWithId() {
        return RoleAssignmentEntity.builder()
                .id(UUID.randomUUID())
                .orgId(ORG_ID).spaceId(SPACE_ID)
                .identityType(IdentityType.ACCOUNT.name())
                .identityId(ACCOUNT_ID)
                .roleCode("R_SPACE_ADMIN")
                .build();
    }
}
