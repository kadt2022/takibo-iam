package com.takibo.identitycore.domain.repository;

import com.takibo.identitycore.domain.rbac.model.BusinessRoleAssignment;

import java.util.List;
import java.util.UUID;

public interface BusinessRoleAssignmentRepository {

    boolean existsByOrgIdAndSpaceIdAndIdentityIdAndBusinessRoleId(
            UUID orgId,
            UUID spaceId,
            UUID identityId,
            UUID businessRoleId
    );

    void saveAll(List<BusinessRoleAssignment> assignments);
}
