package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.infrastructure.entity.RoleEntity;
import com.takibo.identitycore.infrastructure.entity.UserRoleEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleRepository;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRoleRepository;
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
public class UserRoleAssignmentService {

    private final JpaRoleRepository roleRepository;
    private final JpaUserRoleRepository userRoleRepository;
    private final Clock clock;
    private final SpaceStatusCheckerCase spaceStatusCheckerCase;

    @Transactional
    public void assignRolesToUser(UUID orgId, SpaceId spaceId, UserId userId, List<String> requestedRoleCodes) {
        //spaceStatusCheckerCase.assertActive(spaceId); // si ton port accepte SpaceId

        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(userId, "userId");

        List<String> normalizedRoleCodes = normalizeRoleCodes(requestedRoleCodes);
        if (normalizedRoleCodes.isEmpty()) {
            return;
        }

        UUID orgUuid = orgId;
        UUID spaceUuid = spaceId.value();   // UUID direct
        UUID userUuid  = userId.value();    // UUID direct

        Map<String, UUID> roleIdByCode = loadRoleIdsByCode(orgUuid, spaceUuid, normalizedRoleCodes);
        assertAllRequestedRolesExist(spaceUuid, normalizedRoleCodes, roleIdByCode);

        List<UserRoleEntity> assignmentsToInsert =
                computeMissingAssignments(orgUuid, spaceUuid, userUuid, roleIdByCode);

        if (!assignmentsToInsert.isEmpty()) {
            saveAssignmentsIdempotently(spaceId, userId, normalizedRoleCodes, assignmentsToInsert);
        }
    }

    private List<String> normalizeRoleCodes(List<String> codes) {
        if (codes == null) return List.of();
        return codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private Map<String, UUID> loadRoleIdsByCode(UUID orgUuid, UUID spaceUuid, List<String> codes) {
        List<RoleEntity> roleEntities = roleRepository.findByOrgIdAndSpaceIdAndCodeIn(orgUuid, spaceUuid, codes);
        return roleEntities.stream()
                .collect(Collectors.toMap(RoleEntity::getCode, RoleEntity::getId));
    }

    private void assertAllRequestedRolesExist(UUID spaceUuid,
                                              List<String> requestedCodes,
                                              Map<String, UUID> roleIdByCode) {
        List<String> missingRoleCodes = requestedCodes.stream()
                .filter(code -> !roleIdByCode.containsKey(code))
                .toList();

        if (!missingRoleCodes.isEmpty()) {
            throw new UserCreationException(
                    "Unknown role codes in port " + spaceUuid + ": " + missingRoleCodes);
        }
    }

    private List<UserRoleEntity> computeMissingAssignments(UUID orgUuid,
                                                           UUID spaceUuid,
                                                           UUID userUuid,
                                                           Map<String, UUID> roleIdByCode) {
        Instant assignedAt = clock.instant();
        List<UserRoleEntity> assignments = new ArrayList<>(roleIdByCode.size());

        for (UUID roleId : roleIdByCode.values()) {
            boolean alreadyAssigned = userRoleRepository
                    .existsByOrgIdAndSpaceIdAndUserIdAndRoleId(orgUuid, spaceUuid, userUuid, roleId);
            if (!alreadyAssigned) {
                assignments.add(UserRoleEntity.builder()
                        .orgId(orgUuid)
                        .spaceId(spaceUuid)
                        .userId(userUuid)
                        .roleId(roleId)
                        .assignedAt(assignedAt)
                        .assignedBy(null)
                        .build());
            }
        }
        return assignments;
    }

    private void saveAssignmentsIdempotently(SpaceId spaceId,
                                             UserId userId,
                                             List<String> normalizedRoleCodes,
                                             List<UserRoleEntity> assignmentsToInsert) {
        try {
            userRoleRepository.saveAllAndFlush(assignmentsToInsert);
        } catch (DataIntegrityViolationException ex) {
            boolean allNowExist = assignmentsToInsert.stream().allMatch(e ->
                    userRoleRepository.existsByOrgIdAndSpaceIdAndUserIdAndRoleId(
                            e.getOrgId(), e.getSpaceId(), e.getUserId(), e.getRoleId()));

            if (allNowExist) {
                if (log.isDebugEnabled()) {
                    log.debug("Idempotent user_roles ignored (port={}, user={}, roles={})",
                            spaceId.value(), userId.value(), normalizedRoleCodes);
                    log.trace("Duplicate key detail", ex);
                }
            } else {
                log.warn("Failed to persist user_roles (port={}, user={}, roles={})",
                        spaceId.value(), userId.value(), normalizedRoleCodes, ex);
                throw ex;
            }
        }
    }
}
