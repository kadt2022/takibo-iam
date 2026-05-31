package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.infrastructure.entity.GroupEntity;
import com.takibo.identitycore.infrastructure.entity.GroupMemberEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupMemberRepository;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupMembershipService {
    private final JpaGroupRepository groupRepository;
    private final JpaGroupMemberRepository groupMemberRepository;
    private final SpaceStatusCheckerCase spaceStatusCheckerCase;
    private final Clock clock;

    @Transactional /* qualifier: "iamTxManager" si multi-DS */
    public void addToGroups(SpaceId spaceId, UserId userId, List<String> groupCodes) {
      //  spaceStatusPort.assertActive(spaceId.getValue());
        List<String> normalizedGroupCodes = normalizeGroupCodes(groupCodes);
        if (normalizedGroupCodes.isEmpty()) return;

        UUID spaceUuid = UUID.fromString(spaceId.value().toString());
        UUID userUuid = userId.value();

        Map<String, UUID> groupIdByCode = loadGroupIdsByCode(spaceUuid, normalizedGroupCodes);
        assertAllRequestedGroupsExist(spaceUuid, normalizedGroupCodes, groupIdByCode);

        Set<UUID> targetGroupIds = new HashSet<>(groupIdByCode.values());
        Set<UUID> existingGroupIds =
                groupMemberRepository.findExistingGroupIds(spaceUuid, userUuid, targetGroupIds);

        targetGroupIds.removeAll(existingGroupIds);
        if (targetGroupIds.isEmpty()) return;

        List<GroupMemberEntity> membershipsToInsert =
                buildMembershipEntities(spaceUuid, userUuid, targetGroupIds, clock.instant());

        saveMembershipsIdempotently(spaceId, userId, normalizedGroupCodes, membershipsToInsert);
    }

    private List<String> normalizeGroupCodes(List<String> codes) {
        if (codes == null) return List.of();
        return codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private Map<String, UUID> loadGroupIdsByCode(UUID spaceUuid, List<String> codes) {
        List<GroupEntity> groups = groupRepository.findBySpaceIdAndCodeIn(spaceUuid, codes);
        return groups.stream().collect(Collectors.toMap(GroupEntity::getCode, GroupEntity::getId));
    }

    private void assertAllRequestedGroupsExist(UUID spaceUuid,
                                               List<String> requestedCodes,
                                               Map<String, UUID> groupIdByCode) {
        List<String> missingCodes = requestedCodes.stream()
                .filter(c -> !groupIdByCode.containsKey(c))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new UserCreationException("Unknown group codes in port " + spaceUuid + ": " + missingCodes);
        }
    }

    private List<GroupMemberEntity> buildMembershipEntities(UUID spaceUuid,
                                                            UUID userUuid,
                                                            Collection<UUID> groupIds,
                                                            Instant assignedAt) {
        return groupIds.stream()
                .map(groupId -> GroupMemberEntity.builder()
                        .spaceId(spaceUuid)
                        .userId(userUuid)
                        .groupId(groupId)
                        .assignedAt(assignedAt)
                        .assignedBy(null)
                        .build())
                .toList();
    }

    private void saveMembershipsIdempotently(SpaceId spaceId,
                                             UserId userId,
                                             List<String> normalizedGroupCodes,
                                             List<GroupMemberEntity> membershipsToInsert) {
        try {
            groupMemberRepository.saveAllAndFlush(membershipsToInsert);
        } catch (DataIntegrityViolationException ex) {
            boolean allNowExist = membershipsToInsert.stream().allMatch(e ->
                    groupMemberRepository.existsBySpaceIdAndUserIdAndGroupId(
                            e.getSpaceId(), e.getUserId(), e.getGroupId()));
            if (allNowExist) {
                if (log.isDebugEnabled()) {
                    log.debug("Idempotent group_members ignored (port={}, user={}, groups={})",
                            spaceId.value(), userId.value(), normalizedGroupCodes);
                    log.trace("Duplicate key detail", ex);
                }
            } else {
                log.warn("Failed to persist group_members (port={}, user={}, groups={})",
                        spaceId.value(), userId.value(), normalizedGroupCodes, ex);
                throw ex;
            }
        }
    }
}
