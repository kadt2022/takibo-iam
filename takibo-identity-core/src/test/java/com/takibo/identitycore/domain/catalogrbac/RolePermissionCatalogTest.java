package com.takibo.identitycore.domain.catalogrbac;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.takibo.identitycore.domain.catalogrbac.TechnicalPermission.*;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalRole.*;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("deprecation")
class RolePermissionCatalogTest {

    private static final Map<TechnicalRole, Set<TechnicalPermission>> EXPECTED_MATRIX =
            Map.ofEntries(
                    entry(PLATFORM_ADMIN, Set.of(
                            PLATFORM_ORGS_READ,
                            PLATFORM_ORGS_CREATE,
                            PLATFORM_ORGS_SUSPEND,
                            PLATFORM_ORGS_DELETE,
                            PLATFORM_POLICY_READ,
                            PLATFORM_POLICY_UPDATE,
                            PLATFORM_AUDIT_READ,
                            PLATFORM_AUDIT_EXPORT)),
                    entry(PLATFORM_AUDITOR, Set.of(
                            PLATFORM_ORGS_READ,
                            PLATFORM_POLICY_READ,
                            PLATFORM_AUDIT_READ)),
                    entry(ORG_OWNER, Set.of(
                            ORG_READ,
                            ORG_UPDATE,
                            ORG_OWNERSHIP_TRANSFER,
                            ORG_DEACTIVATE,
                            ORG_DELETION_REQUEST,
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
                            ORG_AUDIT_EXPORT)),
                    entry(ORG_ADMIN, Set.of(
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
                            ORG_AUDIT_EXPORT)),
                    entry(ORG_USER_ADMIN, Set.of(
                            ORG_READ,
                            ORG_USERS_READ,
                            ORG_USERS_MANAGE,
                            ORG_USERS_LIFECYCLE,
                            ORG_RBAC_READ)),
                    entry(ORG_CLIENT_ADMIN, Set.of(
                            ORG_READ,
                            ORG_CLIENTS_READ,
                            ORG_CLIENTS_MANAGE,
                            ORG_CLIENTS_ROTATE_SECRET,
                            ORG_CLIENTS_LIFECYCLE)),
                    entry(ORG_AUDITOR, Set.of(
                            ORG_READ,
                            ORG_POLICY_READ,
                            ORG_AUDIT_READ,
                            ORG_AUDIT_EXPORT)),
                    entry(SPACE_ADMIN, Set.of(
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
                            SPACE_AUDIT_READ)),
                    entry(SPACE_USER_ADMIN, Set.of(
                            SPACE_READ,
                            SPACE_USERS_READ,
                            SPACE_USERS_MANAGE,
                            SPACE_USERS_LIFECYCLE)),
                    entry(SPACE_CLIENT_ADMIN, Set.of(
                            SPACE_READ,
                            SPACE_CLIENTS_READ,
                            SPACE_CLIENTS_MANAGE,
                            SPACE_CLIENTS_ROTATE_SECRET,
                            SPACE_CLIENTS_LIFECYCLE)),
                    entry(SPACE_AUDITOR, Set.of(
                            SPACE_READ,
                            SPACE_AUDIT_READ,
                            SPACE_AUDIT_EXPORT)));

    private final RolePermissionCatalog catalog = new RolePermissionCatalog();

    @Test
    void matrix_matchesTheElevenNormativeRolePermissionSetsExactly() {
        assertThat(TechnicalRole.canonicalValues())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_MATRIX.keySet())
                .allSatisfy(role -> assertThat(catalog.permissionsFor(role))
                        .as(role.code())
                        .containsExactlyInAnyOrderElementsOf(EXPECTED_MATRIX.get(role)));
    }

    @Test
    void everyCanonicalRoleContainsOnlyPermissionsFromItsOwnPlan() {
        assertThat(TechnicalRole.canonicalValues())
                .allSatisfy(role -> assertThat(catalog.permissionsFor(role))
                        .as(role.code())
                        .allMatch(permission -> permission.plan() == role.plan()));
    }

    @Test
    void organizationOwnerStrictlyExtendsAdminByTheThreeOwnershipPermissions() {
        Set<TechnicalPermission> adminPermissions = catalog.permissionsFor(ORG_ADMIN);
        Set<TechnicalPermission> ownerPermissions = catalog.permissionsFor(ORG_OWNER);

        assertThat(ownerPermissions)
                .containsAll(adminPermissions)
                .hasSize(adminPermissions.size() + 3);

        EnumSet<TechnicalPermission> ownerOnly = EnumSet.copyOf(ownerPermissions);
        ownerOnly.removeAll(adminPermissions);
        assertThat(ownerOnly).containsExactlyInAnyOrder(
                ORG_OWNERSHIP_TRANSFER,
                ORG_DEACTIVATE,
                ORG_DELETION_REQUEST);
    }

    @Test
    void sensitiveExclusionsAreExplicitlyDenied() {
        assertThat(catalog.permissionsFor(PLATFORM_AUDITOR))
                .doesNotContain(PLATFORM_AUDIT_EXPORT);
        assertThat(catalog.permissionsFor(SPACE_ADMIN))
                .doesNotContain(
                        ORG_SPACES_CREATE,
                        ORG_SPACES_DELETE,
                        SPACE_AUDIT_EXPORT);
        assertThat(catalog.permissionsFor(ORG_USER_ADMIN))
                .doesNotContain(ORG_RBAC_ASSIGN);
    }

    @Test
    void absentPermissionsAndDeprecatedRolesAreDeniedByDefault() {
        assertThat(catalog.grants(SPACE_AUDITOR, SPACE_POLICY_READ)).isFalse();
        assertThat(EnumSet.of(ORG_VIEWER, SPACE_VIEWER, SELF))
                .allSatisfy(role -> assertThat(catalog.permissionsFor(role))
                        .as(role.code())
                        .isEmpty());
    }

    @Test
    void returnedPermissionSetsAreImmutable() {
        Set<TechnicalPermission> permissions = catalog.permissionsFor(SPACE_ADMIN);

        assertThatThrownBy(() -> permissions.add(SPACE_AUDIT_EXPORT))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
