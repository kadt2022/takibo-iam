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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GovernanceRoleAssignmentRepositoryAdapterTest {

    private static final UUID ORG_ID     = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private JpaRoleAssignmentRepository jpa;
    @Mock private RoleJpaAssignmentMapper mapper;

    @InjectMocks
    private GovernanceRoleAssignmentRepositoryAdapter adapter;

    @Test
    void saveGovernanceAssignment_mapsToEntityFlushesAndReturnsDomain() {
        RoleAssignment domain = technicalAssignment(null);
        RoleAssignmentEntity entity = entityWithoutId();
        RoleAssignmentEntity saved = entityWithId();
        RoleAssignment result = mock(RoleAssignment.class);

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(result);

        assertThat(adapter.saveGovernanceAssignment(domain)).isSameAs(result);
        verify(jpa).saveAndFlush(entity);
    }

    @Test
    void saveGovernanceAssignment_entityWithoutId_generatesIdBeforePersisting() {
        RoleAssignment domain = technicalAssignment(null);
        RoleAssignmentEntity entity = entityWithoutId();

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.saveAndFlush(entity)).thenReturn(entityWithId());
        when(mapper.toDomain(any())).thenReturn(mock(RoleAssignment.class));

        adapter.saveGovernanceAssignment(domain);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    void saveGovernanceAssignment_entityWithExistingId_doesNotOverwriteId() {
        UUID existingId = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
        RoleAssignment domain = technicalAssignment(null);
        RoleAssignmentEntity entity = entityWithId(existingId);

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDomain(any())).thenReturn(mock(RoleAssignment.class));

        adapter.saveGovernanceAssignment(domain);

        assertThat(entity.getId()).isEqualTo(existingId);
    }

    @Test
    void saveGovernanceAssignment_businessSourceRejected() {
        RoleAssignment business = new RoleAssignment(
                null, ORG_ID, SPACE_ID,
                new Identity(IdentityType.ACCOUNT, ACCOUNT_ID),
                null, RoleSource.BUSINESS, UUID.randomUUID(),
                Instant.now(), "system", null, null
        );

        assertThatThrownBy(() -> adapter.saveGovernanceAssignment(business))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TECHNICAL or GOVERNANCE source");

        verify(jpa, never()).saveAndFlush(any());
    }

    @Test
    void saveGovernanceAssignment_codeBasedSourceWithoutRoleCodeRejected() {
        RoleAssignment withoutCode = new RoleAssignment(
                null, ORG_ID, SPACE_ID,
                new Identity(IdentityType.ACCOUNT, ACCOUNT_ID),
                null, RoleSource.TECHNICAL, null,
                Instant.now(), "system", null, null
        );

        assertThatThrownBy(() -> adapter.saveGovernanceAssignment(withoutCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roleCode");

        verify(jpa, never()).saveAndFlush(any());
    }

    @Test
    void saveGovernanceAssignment_codeBasedSourceWithBusinessRoleIdRejected() {
        RoleAssignment withBusinessId = new RoleAssignment(
                null, ORG_ID, SPACE_ID,
                new Identity(IdentityType.ACCOUNT, ACCOUNT_ID),
                "R_SPACE_ADMIN", RoleSource.GOVERNANCE, UUID.randomUUID(),
                Instant.now(), "system", null, null
        );

        assertThatThrownBy(() -> adapter.saveGovernanceAssignment(withBusinessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no businessRoleId");

        verify(jpa, never()).saveAndFlush(any());
    }

    @Test
    void saveGovernanceAssignment_dataIntegrityViolation_withSpaceId_throwsDuplicateException() {
        RoleAssignment domain = technicalAssignment(null);
        RoleAssignmentEntity entity = entityWithoutId();

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> adapter.saveGovernanceAssignment(domain))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageContaining("R_SPACE_ADMIN")
                .hasMessageContaining("and space");
    }

    @Test
    void saveGovernanceAssignment_dataIntegrityViolation_withoutSpaceId_messageOmitsSpace() {
        RoleAssignment domain = technicalAssignmentWithoutSpace();
        RoleAssignmentEntity entity = entityWithoutId();

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpa.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> adapter.saveGovernanceAssignment(domain))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageNotContaining("and space");
    }

    @Test
    void findAssignedTechnicalRoleCodes_filtersTechnicalAccountRolesInRequestedBoundaryAndDeduplicates() {
        when(jpa.findByOrgIdAndIdentityId(ORG_ID, ACCOUNT_ID)).thenReturn(List.of(
                assignment("ACCOUNT", RoleSource.TECHNICAL, null, "R_ORG_OWNER"),
                assignment("ACCOUNT", RoleSource.TECHNICAL, SPACE_ID, "R_SPACE_ADMIN"),
                assignment("ACCOUNT", RoleSource.TECHNICAL, SPACE_ID, "R_SPACE_ADMIN"),
                assignment("ACCOUNT", RoleSource.BUSINESS, SPACE_ID, "R_BUSINESS"),
                assignment("USER", RoleSource.TECHNICAL, SPACE_ID, "R_USER"),
                assignment("ACCOUNT", RoleSource.TECHNICAL, UUID.fromString("ffffffff-0000-0000-0000-000000000006"), "R_OTHER_SPACE"),
                assignment("ACCOUNT", RoleSource.TECHNICAL, SPACE_ID, null)
        ));

        List<String> result = adapter.findAssignedTechnicalRoleCodes(ORG_ID, SPACE_ID, ACCOUNT_ID);

        assertThat(result).containsExactly("R_ORG_OWNER", "R_SPACE_ADMIN");
    }

    @Test
    void findDirectAssignments_filtersBusinessSourceBeforeMapping() {
        RoleAssignmentEntity technical = assignment("ACCOUNT", RoleSource.TECHNICAL, SPACE_ID, "R_SPACE_ADMIN");
        RoleAssignmentEntity governance = assignment("ACCOUNT", RoleSource.GOVERNANCE, SPACE_ID, "GOV_LOCAL");
        RoleAssignmentEntity business = assignment("ACCOUNT", RoleSource.BUSINESS, SPACE_ID, "B_APPROVER");
        RoleAssignment technicalDomain = technicalAssignment(null);
        RoleAssignment governanceDomain = technicalAssignmentWithoutSpace();

        when(jpa.findDirectByOrgAndSpaceAndAccount(ORG_ID, SPACE_ID, ACCOUNT_ID))
                .thenReturn(List.of(technical, business, governance));
        when(mapper.toDomain(technical)).thenReturn(technicalDomain);
        when(mapper.toDomain(governance)).thenReturn(governanceDomain);

        List<RoleAssignment> result = adapter.findDirectAssignments(ORG_ID, SPACE_ID, ACCOUNT_ID);

        assertThat(result).containsExactly(technicalDomain, governanceDomain);
        verify(mapper, never()).toDomain(business);
    }

    @Test
    void findOrgLevelAssignments_filtersBusinessSourceBeforeMapping() {
        RoleAssignmentEntity governance = assignment("ACCOUNT", RoleSource.GOVERNANCE, null, "R_ORG_ADMIN");
        RoleAssignmentEntity business = assignment("ACCOUNT", RoleSource.BUSINESS, null, "B_APPROVER");
        RoleAssignment governanceDomain = technicalAssignmentWithoutSpace();

        when(jpa.findOrgLevelByOrgAndAccount(ORG_ID, ACCOUNT_ID)).thenReturn(List.of(business, governance));
        when(mapper.toDomain(governance)).thenReturn(governanceDomain);

        List<RoleAssignment> result = adapter.findOrgLevelAssignments(ORG_ID, ACCOUNT_ID);

        assertThat(result).containsExactly(governanceDomain);
        verify(mapper, never()).toDomain(business);
    }

    private RoleAssignment technicalAssignment(UUID id) {
        return new RoleAssignment(
                id, ORG_ID, SPACE_ID,
                new Identity(IdentityType.ACCOUNT, ACCOUNT_ID),
                "R_SPACE_ADMIN", RoleSource.TECHNICAL, null,
                Instant.now(), "system", null, null
        );
    }

    private RoleAssignment technicalAssignmentWithoutSpace() {
        return new RoleAssignment(
                null, ORG_ID, null,
                new Identity(IdentityType.ACCOUNT, ACCOUNT_ID),
                "R_ORG_ADMIN", RoleSource.TECHNICAL, null,
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
        return entityWithId(UUID.randomUUID());
    }

    private RoleAssignmentEntity entityWithId(UUID id) {
        return RoleAssignmentEntity.builder()
                .id(id)
                .orgId(ORG_ID).spaceId(SPACE_ID)
                .identityType(IdentityType.ACCOUNT.name())
                .identityId(ACCOUNT_ID)
                .roleCode("R_SPACE_ADMIN")
                .build();
    }

    private RoleAssignmentEntity assignment(String identityType, RoleSource roleSource, UUID spaceId, String roleCode) {
        return RoleAssignmentEntity.builder()
                .id(UUID.randomUUID())
                .orgId(ORG_ID)
                .spaceId(spaceId)
                .identityType(identityType)
                .identityId(ACCOUNT_ID)
                .roleSource(roleSource)
                .roleCode(roleCode)
                .build();
    }
}
