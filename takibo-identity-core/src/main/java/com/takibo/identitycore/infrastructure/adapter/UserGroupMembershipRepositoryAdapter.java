package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.rbac.model.UserGroupMembership;
import com.takibo.identitycore.domain.rbac.repository.UserGroupMembershipRepository;
import com.takibo.identitycore.infrastructure.entity.GroupMemberEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class UserGroupMembershipRepositoryAdapter implements UserGroupMembershipRepository {

    private final JpaGroupMemberRepository jpaGroupMemberRepository;
    private final PlatformTransactionManager transactionManager;

    @Override
    public Set<UUID> findExistingGroupIds(UUID organizationId, UUID spaceId, UUID userId, Collection<UUID> groupIds) {
        return jpaGroupMemberRepository.findExistingGroupIds(organizationId, spaceId, userId, Set.copyOf(groupIds));
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
            executeInNewTransaction(() -> {
                jpaGroupMemberRepository.saveAllAndFlush(entities);
                return null;
            }, false);
        } catch (DataIntegrityViolationException ex) {
            boolean allNowExist = executeInNewTransaction(
                    () -> entities.stream().allMatch(entity ->
                            jpaGroupMemberRepository.existsByOrgIdAndSpaceIdAndUserIdAndGroupId(
                                    entity.getOrgId(), entity.getSpaceId(), entity.getUserId(), entity.getGroupId())),
                    true
            );
            if (!allNowExist) {
                throw ex;
            }
        }
    }

    private GroupMemberEntity toEntity(UserGroupMembership membership) {
        return GroupMemberEntity.builder()
                .orgId(membership.organizationId())
                .spaceId(membership.spaceId())
                .userId(membership.userId())
                .groupId(membership.groupId())
                .assignedAt(membership.assignedAt())
                .assignedBy(toAssignedBy(membership.assignedBy()))
                .build();
    }

    private UUID toAssignedBy(String assignedBy) {
        return assignedBy == null ? null : UUID.fromString(assignedBy);
    }

    private <T> T executeInNewTransaction(Supplier<T> action, boolean readOnly) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(readOnly);
        return template.execute(status -> action.get());
    }
}
