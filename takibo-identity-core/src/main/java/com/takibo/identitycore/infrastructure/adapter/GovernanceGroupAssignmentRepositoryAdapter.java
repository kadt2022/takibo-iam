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

import java.util.List;
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

    @Override
    @Transactional(readOnly = true)
    public List<GroupAssignment> findDirectMemberships(UUID orgId, UUID spaceId, UUID accountId) {
        return jpa.findDirectByOrgAndSpaceAndAccount(orgId, spaceId, accountId).stream()
                .filter(e -> !GroupSource.BUSINESS.name().equals(e.getGroupSource()))
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsMembership(UUID orgId, UUID spaceId, UUID accountId, String groupCode) {
        return jpa.existsDirectMembership(orgId, spaceId, accountId, groupCode);
    }

    @Override
    @Transactional
    public int deleteMembership(UUID orgId, UUID spaceId, UUID accountId, String groupCode) {
        return jpa.deleteDirectMembership(orgId, spaceId, accountId, groupCode);
    }

    @Override
    @Transactional(readOnly = true)
    public long countIdentitiesInGroup(UUID orgId, UUID spaceId, String groupCode) {
        return jpa.countDistinctIdentitiesInGroup(orgId, spaceId, groupCode);
    }

    private void assertGovernanceShape(GroupAssignment assignment) {
        boolean codeBased = assignment.groupSource() == GroupSource.TECHNICAL
                || assignment.groupSource() == GroupSource.GOVERNANCE;
        if (!codeBased || assignment.groupCode() == null || assignment.businessGroupId() != null) {
            throw new IllegalArgumentException(
                    "Governance group assignment must use a groupCode with TECHNICAL or GOVERNANCE source"
                            + " and no businessGroupId");
        }
    }
}
