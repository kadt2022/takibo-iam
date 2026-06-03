package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.domain.model.GroupRole;
import com.takibo.identitycore.domain.repository.GroupRoleRepository;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupRoleApplicationService {

    private final GroupRoleRepository groupRoleRepository;

    public void ensureGroupHasRole(UUID spaceId, UUID groupId, UUID roleId) {
        GroupRole groupRole = GroupRole.create(
                SpaceId.of(spaceId),
                GroupId.of(groupId),
                RoleId.of(roleId)
        );

        groupRoleRepository.save(groupRole);
    }
}
