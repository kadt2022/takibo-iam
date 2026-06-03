package com.takibo.identitycore.domain.rbac.repository;

import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;

public interface GovernanceGroupAssignmentRepository {

    /** @throws DuplicateAssignmentException if the assignment already exists */
    GroupAssignment saveGovernanceAssignment(GroupAssignment assignment);
}
