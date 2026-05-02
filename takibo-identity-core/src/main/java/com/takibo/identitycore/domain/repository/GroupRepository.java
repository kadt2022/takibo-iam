package com.takibo.identitycore.domain.repository;

import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.GroupEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository {
    boolean existsBySpaceIdAndCode(SpaceId spaceId, String groupCode);


    List<GroupEntity> findBySpaceIdAndCodeIn(UUID space, List<String> cleaned);

    Optional<Group> findBySpaceIdAndCode(SpaceId spaceId, String code);

    Optional<GroupId> findIdBySpaceIdAndCode(SpaceId spaceId, String code);

    Group save(Group group);

  //  boolean existsBySpaceIdAndCode(SpaceId spaceId, String code);
}
