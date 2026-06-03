package com.takibo.identitycore.application.rbac.governance.port.in;

import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;

import java.util.UUID;

public interface GroupAssignmentCase {

    GroupAssignment assignTechnicalGroup(UUID orgId,
                                         UUID spaceId,
                                         Identity identity,
                                         String technicalGroupCode,
                                         String createdBy);
}
