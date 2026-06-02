package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.rbac.model.UserGovernanceRoleAssignment;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.repository.UserGovernanceRoleRepository;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserRoleAssignmentService {

    private final RoleRepository roleRepository;
    private final UserGovernanceRoleRepository userGovernanceRoleRepository;
    private final SpaceStatusCheckerCase spaceStatusCheckerCase;
    private final Clock clock;

    @Transactional
    public void assignRolesToUser(UUID orgId, SpaceId spaceId, UserId userId, List<String> requestedRoleCodes) {
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(userId, "userId");

        spaceStatusCheckerCase.assertSpaceExistsAndActive(spaceId.value());

        List<String> roleCodes = normalizeRoleCodes(requestedRoleCodes);
        if (roleCodes.isEmpty()) {
            return;
        }

        List<Role> roles = roleRepository.findGovernanceRolesByOrgAndSpaceAndCodes(
                orgId, spaceId.value(), roleCodes);
        assertAllRequestedRolesExist(spaceId.value(), roleCodes, roles);

        List<UserGovernanceRoleAssignment> assignments =
                buildMissingAssignments(orgId, spaceId.value(), userId.value(), roles);
        if (!assignments.isEmpty()) {
            userGovernanceRoleRepository.saveAll(assignments);
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

    private void assertAllRequestedRolesExist(UUID spaceId, List<String> requestedCodes, List<Role> foundRoles) {
        Map<String, Role> byCode = foundRoles.stream()
                .collect(Collectors.toMap(Role::getCode, r -> r));

        List<String> missing = requestedCodes.stream()
                .filter(code -> !byCode.containsKey(code))
                .toList();

        if (!missing.isEmpty()) {
            throw new UserCreationException(
                    "Unknown governance role codes in space " + spaceId + ": " + missing);
        }
    }

    private List<UserGovernanceRoleAssignment> buildMissingAssignments(
            UUID orgId, UUID spaceId, UUID userId, List<Role> roles) {
        Instant now = clock.instant();
        return roles.stream()
                .filter(role -> !userGovernanceRoleRepository
                        .existsByOrgIdAndSpaceIdAndUserIdAndGovernanceRoleId(
                                orgId, spaceId, userId, role.getId().getValue()))
                .map(role -> new UserGovernanceRoleAssignment(
                        orgId, spaceId, userId, role.getId().getValue(), now))
                .toList();
    }
}
