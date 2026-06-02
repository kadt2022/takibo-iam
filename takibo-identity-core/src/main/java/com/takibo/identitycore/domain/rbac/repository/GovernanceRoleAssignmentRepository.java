package com.takibo.identitycore.domain.rbac.repository;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;

public interface GovernanceRoleAssignmentRepository {

    /** @throws DuplicateAssignmentException if the assignment already exists */
    RoleAssignment saveGovernanceAssignment(RoleAssignment assignment);
}
