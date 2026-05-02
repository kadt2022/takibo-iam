package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.repository.GroupRepository;
import com.takibo.identitycore.infrastructure.entity.GroupEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.GroupJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GroupRepositoryAdapter implements GroupRepository {
    private final JpaGroupRepository jpa;
    private final GroupJpaMapper mapper;

    @Override
    public boolean existsBySpaceIdAndCode(SpaceId spaceId, String groupCode) {
        return jpa.existsBySpaceIdAndCode(spaceId.value(), groupCode);
    }

    @Override
    public List<GroupEntity> findBySpaceIdAndCodeIn(UUID space, List<String> cleaned) {
        return jpa.findBySpaceIdAndCodeIn(space, cleaned);
    }

    @Override
    public Optional<Group> findBySpaceIdAndCode(SpaceId spaceId, String code) {
        return jpa.findBySpaceIdAndCode(spaceId.value(), code)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<GroupId> findIdBySpaceIdAndCode(SpaceId spaceId, String code) {
        return jpa.findIdBySpaceIdAndCode(spaceId.value(), code)
                .map(GroupId::new);
    }

    @Override
    @Transactional
    public Group save(Group group) {
        GroupEntity entity = mapper.toEntity(group);
        GroupEntity saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }
}
