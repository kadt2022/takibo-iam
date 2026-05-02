package com.takibo.identitycore.domain.repository;

import com.takibo.identitycore.domain.model.GroupRole;

import java.util.UUID;

public interface GroupRoleRepository {
    boolean existsBySpaceIdAndGroupIdAndRoleId(UUID spaceId, UUID groupId, UUID roleId);

    public GroupRole save(GroupRole link);
}
