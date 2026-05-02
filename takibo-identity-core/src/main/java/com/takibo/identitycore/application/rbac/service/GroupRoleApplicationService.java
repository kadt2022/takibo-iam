package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.model.GroupRole;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.repository.GroupRoleRepository;
import com.takibo.identitycore.infrastructure.entity.GroupRoleEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupRolesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupRoleApplicationService {

    private final JpaGroupRolesRepository groupRoles;
    private final  GroupRoleRepository groupRoleRepository;


    public void ensureGroupHasRole(UUID spaceId, UUID groupId, UUID roleId) {
        if (!groupRoles.existsBySpaceIdAndGroupIdAndRoleId(spaceId, groupId, roleId)) {
            GroupRoleEntity gr = new GroupRoleEntity();
            gr.setSpaceId(spaceId);
            gr.setGroupId(groupId);
            gr.setRoleId(roleId);
            gr.setAssignedAt(Instant.now());

            try {
                groupRoles.save(gr);
            } catch (DataIntegrityViolationException e) {
                // En cas de course (un autre thread a inséré entre-temps) : on ignore.
            }
        }
    }

    public void ensureGroupHasRole2(UUID spaceId, UUID groupId, UUID roleId) {
        if (!groupRoles.existsBySpaceIdAndGroupIdAndRoleId(spaceId, groupId, roleId)) {

            GroupRole groupRole =   GroupRole.create(SpaceId.of(spaceId), GroupId.of(groupId), RoleId.of(roleId));

            groupRoleRepository.save(groupRole);
        }
    }
}
