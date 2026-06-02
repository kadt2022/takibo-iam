package com.takibo.identitycore.application.rbac.governance.service;

import com.takibo.identitycore.application.rbac.governance.port.GroupAssignmentCase;
import com.takibo.identitycore.domain.catalogrbac.TechnicalGroup;
import com.takibo.identitycore.domain.catalogrbac.TechnicalScope;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.rbac.model.GroupSource;
import com.takibo.identitycore.infrastructure.entity.GroupAssignmentEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.GroupAssignmentMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupAssignmentCaseImpl implements GroupAssignmentCase {

    private final JpaGroupAssignmentRepository jpaGroupAssignmentRepository;
    private final GroupAssignmentMapper groupAssignmentMapper;

    @Override
    @Transactional
    public GroupAssignment assignTechnicalGroup(UUID orgId,
                                                UUID spaceId,
                                                Identity identity,
                                                String technicalGroupCode,
                                                String createdBy) {

        TechnicalGroup group = TechnicalGroup.fromCode(technicalGroupCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown technical group: " + technicalGroupCode));

        validateTechnicalGroupScope(group, orgId, spaceId);

        GroupAssignment assignment = new GroupAssignment(
                null, orgId, spaceId,
                identity.id(), identity, identity.type(),
                group.code(), GroupSource.TECHNICAL, null,
                Instant.now(), createdBy, null, null
        );

        GroupAssignmentEntity entity = groupAssignmentMapper.toEntity(assignment);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        GroupAssignmentEntity saved = jpaGroupAssignmentRepository.save(entity);
        return groupAssignmentMapper.toDomain(saved);
    }

    private void validateTechnicalGroupScope(TechnicalGroup group, UUID orgId, UUID spaceId) {
        TechnicalScope scope = group.scope();

        if (scope == TechnicalScope.SYSTEM) {
            if (orgId != null || spaceId != null) {
                throw new IllegalArgumentException("System group " + group.code() + " must not be scoped to org/space");
            }
        }

        if (scope == TechnicalScope.ORGANIZATION) {
            if (orgId == null) {
                throw new IllegalArgumentException("Organization group " + group.code() + " requires orgId");
            }
        }

        if (scope == TechnicalScope.SPACE) {
            if (orgId == null || spaceId == null) {
                throw new IllegalArgumentException("Space group " + group.code() + " requires orgId and spaceId");
            }
        }
    }
}
