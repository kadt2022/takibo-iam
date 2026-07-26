package com.takibo.identitycore.domain.catalogrbac;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.takibo.identitycore.domain.catalogrbac.TechnicalPermission.*;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalRole.*;

/**
 * Single normative source for the canonical role-to-permission matrix.
 *
 * <p>The matrix is immutable, complete for all canonical roles and closed by default:
 * deprecated roles and permissions absent from a role entry grant nothing.</p>
 */
public final class RolePermissionCatalog {

    private static final Set<TechnicalPermission> NO_PERMISSIONS = Set.of();

    private final Map<TechnicalRole, Set<TechnicalPermission>> matrix;

    public RolePermissionCatalog() {
        this.matrix = buildMatrix();
    }

    public Set<TechnicalPermission> permissionsFor(TechnicalRole role) {
        Objects.requireNonNull(role, "role");
        return matrix.getOrDefault(role, NO_PERMISSIONS);
    }

    public boolean grants(TechnicalRole role, TechnicalPermission permission) {
        Objects.requireNonNull(permission, "permission");
        return permissionsFor(role).contains(permission);
    }

    private static Map<TechnicalRole, Set<TechnicalPermission>> buildMatrix() {
        EnumMap<TechnicalRole, Set<TechnicalPermission>> permissionsByRole =
                new EnumMap<>(TechnicalRole.class);

        permissionsByRole.put(PLATFORM_ADMIN, allPermissionsFor(AuthorityPlan.PLATFORM));
        permissionsByRole.put(PLATFORM_AUDITOR, permissions(
                PLATFORM_ORGS_READ,
                PLATFORM_POLICY_READ,
                PLATFORM_AUDIT_READ));

        Set<TechnicalPermission> organizationAdminPermissions = permissions(
                ORG_READ,
                ORG_UPDATE,
                ORG_SPACES_READ,
                ORG_SPACES_CREATE,
                ORG_SPACES_MANAGE,
                ORG_SPACES_DELETE,
                ORG_USERS_READ,
                ORG_USERS_MANAGE,
                ORG_USERS_LIFECYCLE,
                ORG_CLIENTS_READ,
                ORG_CLIENTS_MANAGE,
                ORG_CLIENTS_ROTATE_SECRET,
                ORG_CLIENTS_LIFECYCLE,
                ORG_RBAC_READ,
                ORG_RBAC_ASSIGN,
                ORG_POLICY_READ,
                ORG_POLICY_UPDATE,
                ORG_AUDIT_READ,
                ORG_AUDIT_EXPORT);
        permissionsByRole.put(ORG_ADMIN, organizationAdminPermissions);
        permissionsByRole.put(ORG_OWNER, withAdditionalPermissions(
                organizationAdminPermissions,
                ORG_OWNERSHIP_TRANSFER,
                ORG_DEACTIVATE,
                ORG_DELETION_REQUEST));
        permissionsByRole.put(ORG_USER_ADMIN, permissions(
                ORG_READ,
                ORG_USERS_READ,
                ORG_USERS_MANAGE,
                ORG_USERS_LIFECYCLE,
                ORG_RBAC_READ));
        permissionsByRole.put(ORG_CLIENT_ADMIN, permissions(
                ORG_READ,
                ORG_CLIENTS_READ,
                ORG_CLIENTS_MANAGE,
                ORG_CLIENTS_ROTATE_SECRET,
                ORG_CLIENTS_LIFECYCLE));
        permissionsByRole.put(ORG_AUDITOR, permissions(
                ORG_READ,
                ORG_POLICY_READ,
                ORG_AUDIT_READ,
                ORG_AUDIT_EXPORT));

        permissionsByRole.put(SPACE_ADMIN, permissions(
                SPACE_READ,
                SPACE_UPDATE,
                SPACE_USERS_READ,
                SPACE_USERS_MANAGE,
                SPACE_USERS_LIFECYCLE,
                SPACE_CLIENTS_READ,
                SPACE_CLIENTS_MANAGE,
                SPACE_CLIENTS_ROTATE_SECRET,
                SPACE_CLIENTS_LIFECYCLE,
                SPACE_RBAC_READ,
                SPACE_RBAC_ASSIGN,
                SPACE_POLICY_READ,
                SPACE_POLICY_UPDATE,
                SPACE_AUDIT_READ));
        permissionsByRole.put(SPACE_USER_ADMIN, permissions(
                SPACE_READ,
                SPACE_USERS_READ,
                SPACE_USERS_MANAGE,
                SPACE_USERS_LIFECYCLE));
        permissionsByRole.put(SPACE_CLIENT_ADMIN, permissions(
                SPACE_READ,
                SPACE_CLIENTS_READ,
                SPACE_CLIENTS_MANAGE,
                SPACE_CLIENTS_ROTATE_SECRET,
                SPACE_CLIENTS_LIFECYCLE));
        permissionsByRole.put(SPACE_AUDITOR, permissions(
                SPACE_READ,
                SPACE_AUDIT_READ,
                SPACE_AUDIT_EXPORT));

        validateCompletenessAndPlanIsolation(permissionsByRole);
        return Collections.unmodifiableMap(permissionsByRole);
    }

    private static Set<TechnicalPermission> allPermissionsFor(AuthorityPlan plan) {
        EnumSet<TechnicalPermission> permissions = EnumSet.noneOf(TechnicalPermission.class);
        Arrays.stream(TechnicalPermission.values())
                .filter(permission -> permission.plan() == plan)
                .forEach(permissions::add);
        return Collections.unmodifiableSet(permissions);
    }

    private static Set<TechnicalPermission> permissions(
            TechnicalPermission first,
            TechnicalPermission... remaining
    ) {
        return Collections.unmodifiableSet(EnumSet.of(first, remaining));
    }

    private static Set<TechnicalPermission> withAdditionalPermissions(
            Set<TechnicalPermission> base,
            TechnicalPermission... additional
    ) {
        EnumSet<TechnicalPermission> permissions = EnumSet.copyOf(base);
        Collections.addAll(permissions, additional);
        return Collections.unmodifiableSet(permissions);
    }

    private static void validateCompletenessAndPlanIsolation(
            Map<TechnicalRole, Set<TechnicalPermission>> permissionsByRole
    ) {
        EnumSet<TechnicalRole> canonicalRoles =
                EnumSet.copyOf(TechnicalRole.canonicalValues());
        if (!permissionsByRole.keySet().equals(canonicalRoles)) {
            throw new IllegalStateException("Canonical role-permission matrix is incomplete");
        }

        permissionsByRole.forEach((role, permissions) -> {
            if (permissions.stream().anyMatch(permission -> permission.plan() != role.plan())) {
                throw new IllegalStateException(
                        "Role " + role.code() + " contains a permission from another authority plan");
            }
        });
    }
}
