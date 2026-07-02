package com.takibo.identitycore.domain.rbac.repository;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;

import java.util.List;
import java.util.UUID;

public interface GovernanceRoleAssignmentRepository {

    /** @throws DuplicateAssignmentException if the assignment already exists */
    RoleAssignment saveGovernanceAssignment(RoleAssignment assignment);

    /**
     * Codes des rôles techniques assignés à un account, situés sur ce space
     * (les assignments org-level sans space sont inclus).
     */
    List<String> findAssignedTechnicalRoleCodes(UUID orgId, UUID spaceId, UUID accountId);
}
