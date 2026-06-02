package com.takibo.identitycore.application.rbac.governance.port;

import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;

import java.util.UUID;

public interface RoleAssignmentCase {

    RoleAssignment assignTechnicalRole(UUID orgId,
                                       UUID spaceId,
                                       Identity identity,
                                       String technicalRoleCode,
                                       String createdBy);
}
