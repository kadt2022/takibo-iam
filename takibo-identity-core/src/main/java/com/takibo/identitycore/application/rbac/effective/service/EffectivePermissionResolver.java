package com.takibo.identitycore.application.rbac.effective.service;

import com.takibo.identitycore.application.rbac.effective.model.EffectivePermissionRequest;
import com.takibo.identitycore.application.rbac.effective.model.PermissionCode;
import com.takibo.identitycore.application.rbac.effective.model.SituatedTechnicalGroup;
import com.takibo.identitycore.application.rbac.effective.model.SituatedTechnicalRole;
import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;
import com.takibo.identitycore.domain.catalogrbac.RolePermissionCatalog;
import com.takibo.identitycore.domain.catalogrbac.TechnicalGroup;
import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.domain.exception.EffectivePermissionResolutionException;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.port.SpaceManagementCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static com.takibo.identitycore.domain.catalogrbac.TechnicalPermission.*;
import static java.util.Map.entry;

/**
 * Resolves canonical permissions for real roles and groups inside one authority boundary.
 *
 * <p>Organization permissions may be projected down to SPACE through the explicit ADR
 * table. No upward projection exists, and roles themselves are never rewritten.</p>
 */
@Service
@RequiredArgsConstructor
public class EffectivePermissionResolver {

    private static final Map<TechnicalPermission, TechnicalPermission> ORGANIZATION_TO_SPACE =
            Map.ofEntries(
                    entry(ORG_SPACES_READ, SPACE_READ),
                    entry(ORG_SPACES_MANAGE, SPACE_UPDATE),
                    entry(ORG_USERS_READ, SPACE_USERS_READ),
                    entry(ORG_USERS_MANAGE, SPACE_USERS_MANAGE),
                    entry(ORG_USERS_LIFECYCLE, SPACE_USERS_LIFECYCLE),
                    entry(ORG_CLIENTS_READ, SPACE_CLIENTS_READ),
                    entry(ORG_CLIENTS_MANAGE, SPACE_CLIENTS_MANAGE),
                    entry(ORG_CLIENTS_ROTATE_SECRET, SPACE_CLIENTS_ROTATE_SECRET),
                    entry(ORG_CLIENTS_LIFECYCLE, SPACE_CLIENTS_LIFECYCLE),
                    entry(ORG_RBAC_READ, SPACE_RBAC_READ),
                    entry(ORG_RBAC_ASSIGN, SPACE_RBAC_ASSIGN),
                    entry(ORG_POLICY_READ, SPACE_POLICY_READ),
                    entry(ORG_POLICY_UPDATE, SPACE_POLICY_UPDATE),
                    entry(ORG_AUDIT_READ, SPACE_AUDIT_READ),
                    entry(ORG_AUDIT_EXPORT, SPACE_AUDIT_EXPORT));

    private final RolePermissionCatalog rolePermissionCatalog;
    private final SpaceManagementCase spaceManagementCase;

    public Set<PermissionCode> resolve(EffectivePermissionRequest request) {
        Objects.requireNonNull(request, "request");
        validateTargetBoundary(request);

        EnumSet<TechnicalRole> applicableRoles = collectApplicableRoles(request);
        EnumSet<TechnicalPermission> permissions = EnumSet.noneOf(TechnicalPermission.class);
        applicableRoles.stream()
                .flatMap(role -> rolePermissionCatalog.permissionsFor(role).stream())
                .map(permission -> permissionForTarget(permission, request.authorityPlan()))
                .flatMap(Optional::stream)
                .forEach(permissions::add);

        TreeSet<PermissionCode> permissionCodes = new TreeSet<>();
        permissions.stream().map(PermissionCode::from).forEach(permissionCodes::add);
        return Collections.unmodifiableSet(permissionCodes);
    }

    private EnumSet<TechnicalRole> collectApplicableRoles(EffectivePermissionRequest request) {
        EnumSet<TechnicalRole> applicableRoles = EnumSet.noneOf(TechnicalRole.class);

        request.roles().stream()
                .filter(role -> isApplicable(role, request))
                .map(SituatedTechnicalRole::role)
                .forEach(applicableRoles::add);

        request.groups().stream()
                .filter(group -> isApplicable(group, request))
                .map(SituatedTechnicalGroup::group)
                .flatMap(group -> group.roles().stream())
                .filter(TechnicalRole::inheritable)
                .forEach(applicableRoles::add);

        return applicableRoles;
    }

    private boolean isApplicable(
            SituatedTechnicalRole assignment,
            EffectivePermissionRequest request
    ) {
        TechnicalRole role = assignment.role();
        if (role.deprecated()) {
            return false;
        }
        if (role.plan() == AuthorityPlan.PLATFORM
                && request.authorityPlan() != AuthorityPlan.PLATFORM) {
            throw refusal("Automatic PLATFORM projection to a tenant is forbidden");
        }

        return switch (request.authorityPlan()) {
            case PLATFORM -> role.plan() == AuthorityPlan.PLATFORM
                    && validatePlatformAssignment(assignment);
            case ORGANIZATION -> role.plan() == AuthorityPlan.ORGANIZATION
                    && validateOrganizationAssignment(assignment, request.orgId());
            case SPACE -> switch (role.plan()) {
                case ORGANIZATION -> validateOrganizationAssignment(assignment, request.orgId());
                case SPACE -> validateSpaceAssignment(
                        assignment.role().code(),
                        assignment.orgId(),
                        assignment.spaceId(),
                        request.orgId(),
                        request.spaceId());
                case PLATFORM -> false;
            };
        };
    }

    private boolean isApplicable(
            SituatedTechnicalGroup assignment,
            EffectivePermissionRequest request
    ) {
        return switch (request.authorityPlan()) {
            case PLATFORM -> false;
            case ORGANIZATION -> assignment.group().plan() == AuthorityPlan.ORGANIZATION
                    && validateOrganizationAssignment(
                            assignment.group().code(),
                            assignment.orgId(),
                            assignment.spaceId(),
                            request.orgId());
            case SPACE -> switch (assignment.group().plan()) {
                case ORGANIZATION -> validateOrganizationAssignment(
                        assignment.group().code(),
                        assignment.orgId(),
                        assignment.spaceId(),
                        request.orgId());
                case SPACE -> validateSpaceAssignment(
                        assignment.group().code(),
                        assignment.orgId(),
                        assignment.spaceId(),
                        request.orgId(),
                        request.spaceId());
                case PLATFORM -> false;
            };
        };
    }

    private Optional<TechnicalPermission> permissionForTarget(
            TechnicalPermission permission,
            AuthorityPlan targetPlan
    ) {
        if (permission.plan() == targetPlan) {
            return Optional.of(permission);
        }
        if (permission.plan() == AuthorityPlan.ORGANIZATION
                && targetPlan == AuthorityPlan.SPACE) {
            return Optional.ofNullable(ORGANIZATION_TO_SPACE.get(permission));
        }
        return Optional.empty();
    }

    private void validateTargetBoundary(EffectivePermissionRequest request) {
        switch (request.authorityPlan()) {
            case PLATFORM -> {
                if (request.orgId() != null || request.spaceId() != null) {
                    throw refusal("PLATFORM resolution cannot carry an organization or space");
                }
            }
            case ORGANIZATION -> {
                if (request.orgId() == null) {
                    throw refusal("ORGANIZATION resolution requires orgId");
                }
                if (request.spaceId() != null) {
                    throw refusal("ORGANIZATION resolution cannot carry spaceId");
                }
            }
            case SPACE -> validateTargetSpace(request.orgId(), request.spaceId());
        }
    }

    private void validateTargetSpace(UUID orgId, UUID spaceId) {
        if (orgId == null) {
            throw refusal("SPACE resolution requires orgId");
        }
        if (spaceId == null) {
            throw refusal("SPACE resolution requires spaceId");
        }
        UUID actualOrgId = spaceManagementCase.findOrgIdBySpaceId(SpaceId.of(spaceId))
                .orElseThrow(() -> refusal("Organization not found for space " + spaceId));
        if (!orgId.equals(actualOrgId)) {
            throw refusal("Space " + spaceId + " does not belong to organization " + orgId);
        }
    }

    private boolean validatePlatformAssignment(SituatedTechnicalRole assignment) {
        if (assignment.orgId() != null || assignment.spaceId() != null) {
            throw refusal("PLATFORM role " + assignment.role().code()
                    + " cannot carry a tenant boundary");
        }
        return true;
    }

    private boolean validateOrganizationAssignment(
            SituatedTechnicalRole assignment,
            UUID targetOrgId
    ) {
        return validateOrganizationAssignment(
                assignment.role().code(),
                assignment.orgId(),
                assignment.spaceId(),
                targetOrgId);
    }

    private boolean validateOrganizationAssignment(
            String code,
            UUID assignmentOrgId,
            UUID assignmentSpaceId,
            UUID targetOrgId
    ) {
        if (assignmentOrgId == null || assignmentSpaceId != null) {
            throw refusal("ORGANIZATION assignment " + code + " has an invalid boundary");
        }
        if (!assignmentOrgId.equals(targetOrgId)) {
            throw refusal("ORGANIZATION assignment " + code
                    + " belongs to another organization");
        }
        return true;
    }

    private boolean validateSpaceAssignment(
            String code,
            UUID assignmentOrgId,
            UUID assignmentSpaceId,
            UUID targetOrgId,
            UUID targetSpaceId
    ) {
        if (assignmentOrgId == null || assignmentSpaceId == null) {
            throw refusal("SPACE assignment " + code + " has an incomplete boundary");
        }
        if (!assignmentOrgId.equals(targetOrgId) || !assignmentSpaceId.equals(targetSpaceId)) {
            throw refusal("SPACE assignment " + code + " belongs to another space");
        }
        return true;
    }

    private EffectivePermissionResolutionException refusal(String message) {
        return new EffectivePermissionResolutionException(message);
    }
}
