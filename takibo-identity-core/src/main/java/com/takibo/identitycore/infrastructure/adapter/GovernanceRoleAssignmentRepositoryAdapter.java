package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceRoleAssignmentRepository;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.RoleJpaAssignmentMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GovernanceRoleAssignmentRepositoryAdapter implements GovernanceRoleAssignmentRepository {

    private final JpaRoleAssignmentRepository jpa;
    private final RoleJpaAssignmentMapper mapper;

    @Override
    @Transactional
    public RoleAssignment saveGovernanceAssignment(RoleAssignment assignment) {
        assertGovernanceShape(assignment);

        RoleAssignmentEntity entity = mapper.toEntity(assignment);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        try {
            RoleAssignmentEntity saved = jpa.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateAssignmentException(
                    "Governance role " + assignment.roleCode()
                            + " already assigned to identity " + assignment.identity().id()
                            + " in org " + assignment.orgId()
                            + (assignment.spaceId() != null ? " and space " + assignment.spaceId() : ""),
                    ex
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findAssignedTechnicalRoleCodes(UUID orgId, UUID spaceId, UUID accountId) {
        return jpa.findByOrgIdAndIdentityId(orgId, accountId).stream()
                .filter(e -> IdentityType.ACCOUNT.name().equals(e.getIdentityType()))
                .filter(e -> e.getRoleSource() == RoleSource.TECHNICAL)
                .filter(e -> e.getSpaceId() == null || e.getSpaceId().equals(spaceId))
                .map(RoleAssignmentEntity::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleAssignment> findDirectAssignments(UUID orgId, UUID spaceId, UUID accountId) {
        return jpa.findDirectByOrgAndSpaceAndAccount(orgId, spaceId, accountId).stream()
                .filter(e -> e.getRoleSource() != RoleSource.BUSINESS)
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsAssignment(UUID orgId, UUID spaceId, UUID accountId, String roleCode) {
        return jpa.existsDirectAssignment(orgId, spaceId, accountId, roleCode);
    }

    @Override
    @Transactional
    public int deleteAssignment(UUID orgId, UUID spaceId, UUID accountId, String roleCode) {
        return jpa.deleteDirectAssignment(orgId, spaceId, accountId, roleCode);
    }

    @Override
    @Transactional(readOnly = true)
    public long countIdentitiesHoldingRole(UUID orgId, UUID spaceId, String roleCode) {
        return jpa.countDistinctIdentitiesHoldingRole(orgId, spaceId, roleCode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleAssignment> findOrgLevelAssignments(UUID orgId, UUID accountId) {
        return jpa.findOrgLevelByOrgAndAccount(orgId, accountId).stream()
                .filter(e -> e.getRoleSource() != RoleSource.BUSINESS)
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int deleteOrgLevelAssignment(UUID orgId, UUID accountId, String roleCode) {
        return jpa.deleteOrgLevelAssignment(orgId, accountId, roleCode);
    }

    private void assertGovernanceShape(RoleAssignment assignment) {
        boolean codeBased = assignment.roleSource() == RoleSource.TECHNICAL
                || assignment.roleSource() == RoleSource.GOVERNANCE;
        if (!codeBased || assignment.roleCode() == null || assignment.businessRoleId() != null) {
            throw new IllegalArgumentException(
                    "Governance role assignment must use a roleCode with TECHNICAL or GOVERNANCE source"
                            + " and no businessRoleId");
        }
    }
}
