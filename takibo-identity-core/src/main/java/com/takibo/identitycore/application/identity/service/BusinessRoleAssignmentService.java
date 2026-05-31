package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import com.takibo.identitycore.infrastructure.entity.RoleEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.RoleJpaAssignmentMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleAssignmentRepository;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleRepository;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaTakiboIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusinessRoleAssignmentService {

    private final JpaRoleRepository roleRepository;
    private final JpaRoleAssignmentRepository roleAssignmentRepository;
    private final RoleJpaAssignmentMapper roleAssignmentMapper;
    private final JpaTakiboIdentityRepository takiboIdentityRepository;

    @Transactional
    public void assignBusinessRoles(UUID orgId, UUID spaceId, UUID identityId, List<String> requestedRoleCodes) {
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(identityId, "identityId");

        List<String> businessRoleCodes = normalizeRoleCodes(requestedRoleCodes);
        if (businessRoleCodes.isEmpty()) {
            return;
        }

        rejectTechnicalRoles(businessRoleCodes);

        lockIdentityForAssignment(orgId, identityId);

        Map<String, UUID> roleIdByCode = loadBusinessRoleIds(orgId, spaceId, businessRoleCodes);
        assertAllRequestedRolesExist(spaceId, businessRoleCodes, roleIdByCode);

        List<RoleAssignmentEntity> assignments = buildMissingAssignments(orgId, spaceId, identityId, roleIdByCode);
        if (!assignments.isEmpty()) {
            saveAssignments(assignments);
        }
    }

    private void lockIdentityForAssignment(UUID orgId, UUID identityId) {
        takiboIdentityRepository.lockByOrgIdAndAccountId(orgId, identityId)
                .orElseThrow(() -> new UserCreationException(
                        "Cannot assign business roles because identity does not exist in organization " + orgId
                ));
    }

    private List<String> normalizeRoleCodes(List<String> codes) {
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

    private void rejectTechnicalRoles(List<String> businessRoleCodes) {
        List<String> technicalCodes = businessRoleCodes.stream()
                .filter(code -> TechnicalRole.fromCode(code).isPresent())
                .toList();

        if (!technicalCodes.isEmpty()) {
            throw new UserCreationException(
                    "Technical role codes cannot be assigned during user registration: " + technicalCodes
            );
        }
    }

    private Map<String, UUID> loadBusinessRoleIds(UUID orgId, UUID spaceId, List<String> businessRoleCodes) {
        List<RoleEntity> roles = roleRepository.findByOrgIdAndSpaceIdAndCodeIn(orgId, spaceId, businessRoleCodes);
        return roles.stream()
                .collect(Collectors.toMap(RoleEntity::getCode, RoleEntity::getId));
    }

    private void assertAllRequestedRolesExist(UUID spaceId,
                                              List<String> requestedCodes,
                                              Map<String, UUID> roleIdByCode) {
        List<String> missingRoleCodes = requestedCodes.stream()
                .filter(code -> !roleIdByCode.containsKey(code))
                .toList();

        if (!missingRoleCodes.isEmpty()) {
            throw new UserCreationException(
                    "Unknown business role codes in space " + spaceId + ": " + missingRoleCodes
            );
        }
    }

    private List<RoleAssignmentEntity> buildMissingAssignments(UUID orgId,
                                                               UUID spaceId,
                                                               UUID identityId,
                                                               Map<String, UUID> roleIdByCode) {
        List<RoleAssignmentEntity> assignments = new ArrayList<>(roleIdByCode.size());
        Identity identity = new Identity(IdentityType.HUMAN, identityId);

        for (UUID businessRoleId : roleIdByCode.values()) {
            boolean alreadyAssigned = roleAssignmentRepository
                    .existsByOrgIdAndSpaceIdAndIdentityTypeAndIdentityIdAndRoleSourceAndBusinessRoleId(
                            orgId,
                            spaceId,
                            IdentityType.HUMAN.name(),
                            identityId,
                            RoleSource.BUSINESS,
                            businessRoleId
                    );

            if (!alreadyAssigned) {
                RoleAssignment assignment = new RoleAssignment(
                        null,
                        orgId,
                        spaceId,
                        identity,
                        null,
                        RoleSource.BUSINESS,
                        businessRoleId,
                        Instant.now(),
                        null,
                        null,
                        null
                );
                assignments.add(roleAssignmentMapper.toEntity(assignment));
            }
        }

        return assignments;
    }

    // Concurrent safety: TakiboIdentity is locked with PESSIMISTIC_WRITE before this call.
    // Two concurrent requests for the same identity cannot both reach this point simultaneously —
    // one will block until the other commits. After the lock, existsBy in buildMissingAssignments
    // is a reliable guard. saveAllAndFlush without a catch is intentional: if a unique violation
    // still occurs here, it means another writer bypassed the canonical locked path and should fail.
    private void saveAssignments(List<RoleAssignmentEntity> assignments) {
        roleAssignmentRepository.saveAllAndFlush(assignments);
    }
}
