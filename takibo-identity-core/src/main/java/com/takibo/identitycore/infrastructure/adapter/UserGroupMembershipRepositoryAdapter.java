package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.rbac.model.UserGroupMembership;
import com.takibo.identitycore.domain.rbac.repository.UserGroupMembershipRepository;
import com.takibo.identitycore.infrastructure.entity.GroupMemberEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserGroupMembershipRepositoryAdapter implements UserGroupMembershipRepository {

    private final JpaGroupMemberRepository jpaGroupMemberRepository;

    @Override
    public Set<UUID> findExistingGroupIds(UUID spaceId, UUID userId, Collection<UUID> groupIds) {
        return jpaGroupMemberRepository.findExistingGroupIds(spaceId, userId, Set.copyOf(groupIds));
    }

    @Override
    public void saveAllIdempotently(List<UserGroupMembership> memberships) {
        if (memberships.isEmpty()) {
            return;
        }

        List<GroupMemberEntity> entities = memberships.stream()
                .map(this::toEntity)
                .toList();

        try {
            jpaGroupMemberRepository.saveAllAndFlush(entities);
        } catch (DataIntegrityViolationException ex) {
            boolean allNowExist = entities.stream().allMatch(entity ->
                    jpaGroupMemberRepository.existsBySpaceIdAndUserIdAndGroupId(
                            entity.getSpaceId(), entity.getUserId(), entity.getGroupId()));
            if (!allNowExist) {
                throw ex;
            }
        }
    }

    private GroupMemberEntity toEntity(UserGroupMembership membership) {
        return GroupMemberEntity.builder()
                .spaceId(membership.spaceId())
                .userId(membership.userId())
                .groupId(membership.groupId())
                .assignedAt(membership.assignedAt())
                .assignedBy(null)
                .build();
    }
}
