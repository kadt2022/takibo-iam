package com.takibo.identitycore.application.rbac.business.service;

import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.rbac.model.BusinessRoleAssignment;
import com.takibo.identitycore.domain.rbac.repository.BusinessRoleAssignmentRepository;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.repository.TakiboIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusinessRoleAssignmentService {

    private final TakiboIdentityRepository takiboIdentityRepository;
    private final RoleRepository roleRepository;
    private final BusinessRoleAssignmentRepository businessRoleAssignmentRepository;

    // Concurrent safety: TakiboIdentity is locked with PESSIMISTIC_WRITE inside the repository.
    // Two concurrent requests for the same identity block at that point. After the lock,
    // existsBy in buildMissingAssignments is a reliable guard. saveAll without catch is intentional:
    // a unique violation here means another writer bypassed the canonical locked path and should fail.
    @Transactional
    public void assignBusinessRoles(UUID orgId, UUID spaceId, UUID accountId, List<String> requestedRoleCodes) {
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(accountId, "accountId");

        List<String> roleCodes = normalizeRoleCodes(requestedRoleCodes);
        if (roleCodes.isEmpty()) {
            return;
        }

        UUID identityId = takiboIdentityRepository
                .lockAndFindIdentityIdByOrgIdAndAccountId(orgId, accountId)
                .orElseThrow(() -> new UserCreationException(
                        "Cannot assign business roles: no TakiboIdentity found for account " + accountId
                                + " in organization " + orgId));

        List<Role> roles = roleRepository.findBusinessRolesByOrgAndSpaceAndCodes(orgId, spaceId, roleCodes);
        assertAllRequestedRolesExist(spaceId, roleCodes, roles);

        List<BusinessRoleAssignment> assignments = buildMissingAssignments(orgId, spaceId, identityId, roles);
        if (!assignments.isEmpty()) {
            businessRoleAssignmentRepository.saveAll(assignments);
        }
    }

    private List<String> normalizeRoleCodes(List<String> codes) {
        if (codes == null) return List.of();
        return codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
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
                    "Unknown business role codes in space " + spaceId + ": " + missing);
        }
    }

    private List<BusinessRoleAssignment> buildMissingAssignments(
            UUID orgId, UUID spaceId, UUID identityId, List<Role> roles) {
        Instant now = Instant.now();
        return roles.stream()
                .filter(role -> !businessRoleAssignmentRepository
                        .existsByOrgIdAndSpaceIdAndIdentityIdAndBusinessRoleId(
                                orgId, spaceId, identityId, role.getId().getValue()))
                .map(role -> new BusinessRoleAssignment(orgId, spaceId, identityId, role.getId().getValue(), now))
                .toList();
    }
}
