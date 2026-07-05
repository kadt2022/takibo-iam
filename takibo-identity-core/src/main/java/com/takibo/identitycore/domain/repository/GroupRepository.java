package com.takibo.identitycore.domain.repository;

import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.rbac.model.GroupReference;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.SpaceId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository {

    boolean existsBySpaceIdAndCode(SpaceId spaceId, String groupCode);

    List<GroupReference> findReferencesBySpaceIdAndCodeIn(UUID spaceId, List<String> groupCodes);

    Optional<Group> findBySpaceIdAndCode(SpaceId spaceId, String code);

    Optional<GroupId> findIdBySpaceIdAndCode(SpaceId spaceId, String code);

    Optional<Group> findById(GroupId id);

    List<Group> findAllByOrgAndSpace(UUID orgId, UUID spaceId);

    Group save(Group group);
}
