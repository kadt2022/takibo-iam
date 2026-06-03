package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.GroupRole;
import com.takibo.identitycore.domain.repository.GroupRoleRepository;
import com.takibo.identitycore.infrastructure.entity.GroupRoleEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.GroupRoleJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupRolesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Component
@RequiredArgsConstructor
public class GroupRoleRepositoryAdapter implements GroupRoleRepository {

    private final GroupRoleJpaMapper mapper;
    private final JpaGroupRolesRepository jpa;

    @Override
    public boolean existsBySpaceIdAndGroupIdAndRoleId(UUID spaceId, UUID groupId, UUID roleId) {
        return jpa.existsBySpaceIdAndGroupIdAndRoleId(spaceId, groupId, roleId);
    }

    @Override
    @Transactional
    public GroupRole save(GroupRole groupRole) {
        if (jpa.existsBySpaceIdAndGroupIdAndRoleId(
                groupRole.getSpaceId().value(),
                groupRole.getGroupId().value(),
                groupRole.getRoleId().value())) {
            return groupRole;
        }

        GroupRoleEntity e = mapper.toEntity(groupRole);
        try {
            GroupRoleEntity saved = jpa.saveAndFlush(e);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            if (jpa.existsBySpaceIdAndGroupIdAndRoleId(
                    groupRole.getSpaceId().value(),
                    groupRole.getGroupId().value(),
                    groupRole.getRoleId().value())) {
                return groupRole;
            }
            throw ex;
        }
    }
}
