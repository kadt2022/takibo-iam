package com.takibo.identitycore.application.rbac.assignement;

import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.domain.catalogrbac.TechnicalScope;
import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.exception.InvalidRoleScopeException;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.application.rbac.assignement.port.RoleAssignmentCase;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.RoleJpaAssignmentMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleAssignmentCaseImpl implements RoleAssignmentCase {

    private static final String INVALID_SYSTEM_ROLE_SCOPE = "System role %s must not be scoped to org/port";
    private static final String MISSING_ORG_ID_FOR_ORG_ROLE = "Organization role %s requires orgId";
    private static final String MISSING_SCOPE_IDS_FOR_SPACE_ROLE = "Space role %s requires orgId and spaceId";
    private static final String MISSING_ORG_ID_FOR_USER_ROLE = "User role %s requires orgId for ownership context";
    private static final String UNKNOWN_SCOPE_TYPE = "Unknown scope type: %s";
    private final JpaRoleAssignmentRepository jpaRoleAssignmentRepository;
    private final RoleJpaAssignmentMapper roleJpaAssignmentMapper;

    @Override
    @Transactional
    public RoleAssignment assignTechnicalRole(UUID orgId,
                                              UUID spaceId,
                                              Identity identity,
                                              String technicalRoleCode,
                                              String createdBy) {

        TechnicalRole role = TechnicalRole.fromCode(technicalRoleCode)
                .orElseThrow(() ->
                        new InvalidRoleScopeException("Unknown technical role: " + technicalRoleCode)
                );

        validateTechnicalRoleScope(role, orgId, spaceId);

        RoleAssignment assignment = new RoleAssignment(
                null,
                orgId,
                spaceId,
                identity,
                role.code(),
                RoleSource.TECHNICAL,
                null,
                Instant.now(),
                createdBy,
                null,
                null
        );

        RoleAssignmentEntity entity = roleJpaAssignmentMapper.toEntity(assignment);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }

        try {
            RoleAssignmentEntity saved = jpaRoleAssignmentRepository.save(entity);
            return roleJpaAssignmentMapper.toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateAssignmentException(
                    "Technical role " + role.code()
                            + " already assigned to identity " + identity.id()
                            + " in org " + orgId
                            + (spaceId != null ? " and port " + spaceId : ""),
                    ex
            );
        }
    }

    private void validateTechnicalRoleScope(TechnicalRole role, UUID orgId, UUID spaceId) {
        TechnicalScope scope = role.scope();

        if (scope == null) {
            throw new InvalidRoleScopeException("Role scope cannot be null for role: " + role.code());
        }

        switch (scope) {
            case SYSTEM:
                validateSystemRole(role, orgId, spaceId);
                break;
            case ORGANIZATION:
                validateOrganizationRole(role, orgId);
                break;
            case SPACE:
                validateSpaceRole(role, orgId, spaceId);
                break;
            case USER:
                validateUserRole(role, orgId);
                break;
            default:
                throw new InvalidRoleScopeException(String.format(UNKNOWN_SCOPE_TYPE, scope));
        }
    }

    private void validateSystemRole(TechnicalRole role, UUID orgId, UUID spaceId) {
        if (orgId != null || spaceId != null) {
            throw new InvalidRoleScopeException(String.format(INVALID_SYSTEM_ROLE_SCOPE, role.code()));
        }
    }

    private void validateOrganizationRole(TechnicalRole role, UUID orgId) {
        if (orgId == null) {
            throw new InvalidRoleScopeException(String.format(MISSING_ORG_ID_FOR_ORG_ROLE, role.code()));
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
