package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.rbac.model.GroupSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceGroupAssignmentRepository;
import com.takibo.identitycore.infrastructure.entity.GroupAssignmentEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.GroupAssignmentMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GovernanceGroupAssignmentRepositoryAdapter implements GovernanceGroupAssignmentRepository {

    private final JpaGroupAssignmentRepository jpa;
    private final GroupAssignmentMapper mapper;

    @Override
    @Transactional
    public GroupAssignment saveGovernanceAssignment(GroupAssignment assignment) {
        assertGovernanceShape(assignment);

        GroupAssignmentEntity entity = mapper.toEntity(assignment);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        try {
            GroupAssignmentEntity saved = jpa.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateAssignmentException(
                    "Governance group " + assignment.groupCode()
                            + " already assigned to identity " + assignment.identityId()
                            + " in org " + assignment.orgId()
                            + (assignment.spaceId() != null ? " and space " + assignment.spaceId() : ""),
                    ex
            );
        }
    }

    private void assertGovernanceShape(GroupAssignment assignment) {
        if (assignment.groupSource() != GroupSource.TECHNICAL
                || assignment.groupCode() == null
                || assignment.businessGroupId() != null) {
            throw new IllegalArgumentException(
                    "Governance group assignment must use a groupCode with TECHNICAL source and no businessGroupId");
        }
    }
}
