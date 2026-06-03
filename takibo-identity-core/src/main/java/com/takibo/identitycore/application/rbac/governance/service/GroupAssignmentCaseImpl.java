package com.takibo.identitycore.application.rbac.governance.service;

import com.takibo.identitycore.application.rbac.governance.port.in.GroupAssignmentCase;
import com.takibo.identitycore.domain.catalogrbac.TechnicalGroup;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.rbac.model.GroupSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceGroupAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupAssignmentCaseImpl implements GroupAssignmentCase {

    private final GovernanceGroupAssignmentRepository governanceGroupAssignmentRepository;

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

        return governanceGroupAssignmentRepository.saveGovernanceAssignment(assignment);
    }

    private void validateTechnicalGroupScope(TechnicalGroup group, UUID orgId, UUID spaceId) {
        switch (group.scope()) {
            case SYSTEM       -> validateSystemGroup(group, orgId, spaceId);
            case ORGANIZATION -> validateOrganizationGroup(group, orgId);
            case SPACE        -> validateSpaceGroup(group, orgId, spaceId);
            default           -> { /* no scope restriction for other types */ }
        }
    }

    private void validateSystemGroup(TechnicalGroup group, UUID orgId, UUID spaceId) {
        if (orgId != null || spaceId != null) {
            throw new IllegalArgumentException("System group " + group.code() + " must not be scoped to org/space");
        }
    }

    private void validateOrganizationGroup(TechnicalGroup group, UUID orgId) {
        if (orgId == null) {
            throw new IllegalArgumentException("Organization group " + group.code() + " requires orgId");
        }
    }

    private void validateSpaceGroup(TechnicalGroup group, UUID orgId, UUID spaceId) {
        if (orgId == null || spaceId == null) {
            throw new IllegalArgumentException("Space group " + group.code() + " requires orgId and spaceId");
        }
    }
}
