package com.takibo.identitycore.application.rbac.assignement.port;

import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.model.Identity;

import java.util.UUID;

public interface GroupAssignmentCase {

    GroupAssignment assignTechnicalGroup(UUID orgId,
                                         UUID spaceId,
                                         Identity identity,
                                         String technicalGroupCode,
                                         String createdBy);
}
