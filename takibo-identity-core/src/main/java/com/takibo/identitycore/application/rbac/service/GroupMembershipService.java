package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.repository.GroupRepository;
import com.takibo.identitycore.domain.rbac.model.GroupReference;
import com.takibo.identitycore.domain.rbac.model.UserGroupMembership;
import com.takibo.identitycore.domain.rbac.repository.UserGroupMembershipRepository;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupMembershipService {

    private final GroupRepository groupRepository;
    private final UserGroupMembershipRepository userGroupMembershipRepository;
    private final SpaceStatusCheckerCase spaceStatusCheckerCase;
    private final Clock clock;

    @Transactional
    public void addToGroups(SpaceId spaceId, UserId userId, List<String> groupCodes) {
        spaceStatusCheckerCase.assertSpaceExistsAndActive(spaceId.value());
        List<String> normalizedGroupCodes = normalizeGroupCodes(groupCodes);
        if (normalizedGroupCodes.isEmpty()) {
            return;
        }

        UUID spaceUuid = spaceId.value();
        UUID userUuid = userId.value();

        Map<String, UUID> groupIdByCode = loadGroupIdsByCode(spaceUuid, normalizedGroupCodes);
        assertAllRequestedGroupsExist(spaceUuid, normalizedGroupCodes, groupIdByCode);

        Set<UUID> targetGroupIds = new HashSet<>(groupIdByCode.values());
        Set<UUID> existingGroupIds =
                userGroupMembershipRepository.findExistingGroupIds(spaceUuid, userUuid, targetGroupIds);

        targetGroupIds.removeAll(existingGroupIds);
        if (targetGroupIds.isEmpty()) {
            return;
        }

        List<UserGroupMembership> membershipsToInsert =
                buildMemberships(spaceUuid, userUuid, targetGroupIds, clock.instant());

        userGroupMembershipRepository.saveAllIdempotently(membershipsToInsert);
    }

    private List<String> normalizeGroupCodes(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();
    }

    private Map<String, UUID> loadGroupIdsByCode(UUID spaceUuid, List<String> codes) {
        List<GroupReference> groups = groupRepository.findReferencesBySpaceIdAndCodeIn(spaceUuid, codes);
        return groups.stream().collect(Collectors.toMap(GroupReference::code, GroupReference::id));
    }

    private void assertAllRequestedGroupsExist(UUID spaceUuid,
                                               List<String> requestedCodes,
                                               Map<String, UUID> groupIdByCode) {
        List<String> missingCodes = requestedCodes.stream()
                .filter(code -> !groupIdByCode.containsKey(code))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new UserCreationException("Unknown business group codes in space " + spaceUuid + ": " + missingCodes);
        }
    }

    private List<UserGroupMembership> buildMemberships(UUID spaceUuid,
                                                       UUID userUuid,
                                                       Collection<UUID> groupIds,
                                                       Instant assignedAt) {
        return groupIds.stream()
                .map(groupId -> new UserGroupMembership(spaceUuid, userUuid, groupId, assignedAt, null))
                .toList();
    }
}
