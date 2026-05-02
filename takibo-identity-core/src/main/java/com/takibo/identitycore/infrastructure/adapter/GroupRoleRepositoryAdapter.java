package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.GroupRole;
import com.takibo.identitycore.domain.repository.GroupRoleRepository;
import com.takibo.identitycore.infrastructure.entity.GroupRoleEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.GroupRoleJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupRolesRepository;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

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
    public GroupRole save(GroupRole groupRole) {
        GroupRoleEntity e = mapper.toEntity(groupRole);
        GroupRoleEntity saved = jpa.save(e);
        return mapper.toDomain(saved);
    }
}
