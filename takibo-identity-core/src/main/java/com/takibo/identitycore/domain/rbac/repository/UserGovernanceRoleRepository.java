package com.takibo.identitycore.domain.rbac.repository;

import com.takibo.identitycore.domain.rbac.model.UserGovernanceRoleAssignment;

import java.util.List;
import java.util.UUID;

public interface UserGovernanceRoleRepository {

    boolean existsByOrgIdAndSpaceIdAndUserIdAndGovernanceRoleId(
            UUID orgId,
            UUID spaceId,
            UUID userId,
            UUID governanceRoleId
    );

    void saveAll(List<UserGovernanceRoleAssignment> assignments);
}
