package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.repository.GovernanceRoleAssignmentRepository;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.RoleJpaAssignmentMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GovernanceRoleAssignmentRepositoryAdapter implements GovernanceRoleAssignmentRepository {

    private final JpaRoleAssignmentRepository jpa;
    private final RoleJpaAssignmentMapper mapper;

    @Override
    @Transactional
    public RoleAssignment save(RoleAssignment assignment) {
        RoleAssignmentEntity entity = mapper.toEntity(assignment);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        try {
            RoleAssignmentEntity saved = jpa.save(entity);
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
}
