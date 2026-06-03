package com.takibo.identitycore.domain.rbac.repository;

import com.takibo.identitycore.domain.rbac.model.UserGroupMembership;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserGroupMembershipRepository {

    Set<UUID> findExistingGroupIds(UUID organizationId, UUID spaceId, UUID userId, Collection<UUID> groupIds);

    void saveAllIdempotently(List<UserGroupMembership> memberships);
}
