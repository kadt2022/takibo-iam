package com.takibo.identitycore.application.rbac.governance.service;

import com.takibo.identitycore.application.rbac.governance.port.in.RoleAssignmentCase;
import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;
import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.domain.exception.InvalidRoleScopeException;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceRoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleAssignmentCaseImpl implements RoleAssignmentCase {

    private static final String INVALID_SYSTEM_ROLE_SCOPE        = "System role %s must not be scoped to org/space";
    private static final String MISSING_ORG_ID_FOR_ORG_ROLE      = "Organization role %s requires orgId";
    private static final String INVALID_SPACE_FOR_ORG_ROLE       =
            "Organization role %s must not be scoped to a space (org authority never depends on a space)";
    private static final String MISSING_SCOPE_IDS_FOR_SPACE_ROLE = "Space role %s requires orgId and spaceId";
    private static final String MISSING_ORG_ID_FOR_USER_ROLE     = "User role %s requires orgId for ownership context";
    private final GovernanceRoleAssignmentRepository governanceRoleAssignmentRepository;

    @Override
    @Transactional
    public RoleAssignment assignTechnicalRole(UUID orgId,
                                              UUID spaceId,
                                              Identity identity,
                                              String technicalRoleCode,
                                              String createdBy) {

        TechnicalRole role = TechnicalRole.fromCode(technicalRoleCode)
                .orElseThrow(() ->
                        new InvalidRoleScopeException("Unknown technical role: " + technicalRoleCode));

        validateTechnicalRoleScope(role, orgId, spaceId);

        RoleAssignment assignment = new RoleAssignment(
                null, orgId, spaceId, identity,
                role.code(), RoleSource.TECHNICAL, null,
                Instant.now(), createdBy, null, null
        );

        return governanceRoleAssignmentRepository.saveGovernanceAssignment(assignment);
    }

    private void validateTechnicalRoleScope(TechnicalRole role, UUID orgId, UUID spaceId) {
        AuthorityPlan plan = role.plan();

        if (plan == null) {
            throw new InvalidRoleScopeException("Role scope cannot be null for role: " + role.code());
        }

        if (role.selfService()) {
            validateUserRole(role, orgId);
            return;
        }

        switch (plan) {
            case PLATFORM     -> validateSystemRole(role, orgId, spaceId);
            case ORGANIZATION -> validateOrganizationRole(role, orgId, spaceId);
            case SPACE        -> validateSpaceRole(role, orgId, spaceId);
        }
    }

    private void validateSystemRole(TechnicalRole role, UUID orgId, UUID spaceId) {
        if (orgId != null || spaceId != null) {
            throw new InvalidRoleScopeException(String.format(INVALID_SYSTEM_ROLE_SCOPE, role.code()));
        }
    }

    /**
     * IAM 31 : une autorité d'organisation ne doit jamais dépendre de l'existence
     * d'un space — l'assignation d'un rôle de scope ORGANIZATION est org-level
     * (space_id NULL), toujours.
     */
    private void validateOrganizationRole(TechnicalRole role, UUID orgId, UUID spaceId) {
        if (orgId == null) {
            throw new InvalidRoleScopeException(String.format(MISSING_ORG_ID_FOR_ORG_ROLE, role.code()));
        }
        if (spaceId != null) {
            throw new InvalidRoleScopeException(String.format(INVALID_SPACE_FOR_ORG_ROLE, role.code()));
        }
    }

    private void validateSpaceRole(TechnicalRole role, UUID orgId, UUID spaceId) {
        if (orgId == null || spaceId == null) {
            throw new InvalidRoleScopeException(String.format(MISSING_SCOPE_IDS_FOR_SPACE_ROLE, role.code()));
        }
    }

    private void validateUserRole(TechnicalRole role, UUID orgId) {
        if (orgId == null) {
            throw new InvalidRoleScopeException(String.format(MISSING_ORG_ID_FOR_USER_ROLE, role.code()));
        }
    }
}
