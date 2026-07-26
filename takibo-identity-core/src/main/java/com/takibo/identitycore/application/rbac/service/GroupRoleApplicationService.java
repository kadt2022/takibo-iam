package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.domain.catalogrbac.TenantRoleCodePolicy;
import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.model.GroupRole;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.repository.GroupRepository;
import com.takibo.identitycore.domain.repository.GroupRoleRepository;
import com.takibo.identitycore.domain.repository.RoleRepository;
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
    private final GroupRepository groupRepository;
    private final RoleRepository roleRepository;

    public void ensureGroupHasRole(UUID spaceId, UUID groupId, UUID roleId) {
        Group group = groupRepository.findById(GroupId.of(groupId))
                .orElseThrow(() -> new UserCreationException("Group not found: " + groupId));

        Role role = roleRepository.findById(RoleId.of(roleId))
                .orElseThrow(() -> new UserCreationException("Role not found: " + roleId));
        TenantRoleCodePolicy.requireTenantCode(role.getCode());

        if (!group.getNature().name().equals(role.getNature().name())) {
            throw new UserCreationException(
                    "Group and role nature mismatch: group is " + group.getNature()
                    + " but role is " + role.getNature());
        }

        GroupRole groupRole = GroupRole.create(
                SpaceId.of(spaceId),
                GroupId.of(groupId),
                RoleId.of(roleId)
        );

        groupRoleRepository.save(groupRole);
    }
}
