package com.takibo.identitycore.domain.catalogrbac;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class CanonicalRbacCatalogTest {

    private final TechnicalRbacCatalog catalog = new DefaultTechnicalRbacCatalog();

    @Test
    void authorityPlans_areExactlyTheThreeAdministrativePlanes() {
        assertThat(AuthorityPlan.values())
                .containsExactly(
                        AuthorityPlan.PLATFORM,
                        AuthorityPlan.ORGANIZATION,
                        AuthorityPlan.SPACE);
    }

    @Test
    void canonicalRoles_areCompleteAndPartitionedByPlan() {
        List<TechnicalRole> roles = catalog.getCanonicalRoles();

        assertThat(roles)
                .hasSize(11)
                .contains(TechnicalRole.SPACE_AUDITOR)
                .allSatisfy(role -> {
                    assertThat(role.code()).startsWith("R_");
                    assertThat(role.displayName()).isNotBlank();
                    assertThat(role.description()).isNotBlank();
                    assertThat(role.plan()).isNotNull();
                    assertThat(role.deprecated()).isFalse();
                });
        assertThat(roles).filteredOn(role -> role.plan() == AuthorityPlan.PLATFORM).hasSize(2);
        assertThat(roles).filteredOn(role -> role.plan() == AuthorityPlan.ORGANIZATION).hasSize(5);
        assertThat(roles).filteredOn(role -> role.plan() == AuthorityPlan.SPACE).hasSize(4);
    }

    @Test
    void roleCodes_areUniqueAcrossCanonicalAndDeprecatedRoles() {
        assertThat(Arrays.stream(TechnicalRole.values()).map(TechnicalRole::code))
                .doesNotHaveDuplicates();
    }

    @Test
    void organizationOwnership_isNeitherAssignableNorInheritable() {
        assertThat(TechnicalRole.ORG_OWNER.assignable()).isFalse();
        assertThat(TechnicalRole.ORG_OWNER.inheritable()).isFalse();
        assertThat(TechnicalRole.ORG_OWNER.administrator()).isTrue();
    }

    @Test
    void organizationAdminGroup_transmitsAdminRoleOnly() {
        assertThat(TechnicalGroup.ORG_ADMINS.roles())
                .containsExactly(TechnicalRole.ORG_ADMIN)
                .doesNotContain(TechnicalRole.ORG_OWNER);
    }

    @Test
    void spaceAuditor_keepsLegacyAuditPermissionsUntilEffectiveRbacIsMigrated() {
        assertThat(TechnicalRole.SPACE_AUDITOR.permissions())
                .extracting(TechnicalGroup.TechnicalPermission::code)
                .containsExactlyInAnyOrder("P_READ_AUDIT_LOGS", "P_EXPORT_AUDIT_LOGS");
    }

    @Test
    void administratorCharacteristic_distinguishesAdministratorsFromAuditors() {
        assertThat(TechnicalRole.canonicalValues())
                .filteredOn(TechnicalRole::administrator)
                .containsExactlyInAnyOrder(
                        TechnicalRole.PLATFORM_ADMIN,
                        TechnicalRole.ORG_OWNER,
                        TechnicalRole.ORG_ADMIN,
                        TechnicalRole.ORG_USER_ADMIN,
                        TechnicalRole.ORG_CLIENT_ADMIN,
                        TechnicalRole.SPACE_ADMIN,
                        TechnicalRole.SPACE_USER_ADMIN,
                        TechnicalRole.SPACE_CLIENT_ADMIN);

        assertThat(TechnicalRole.canonicalValues())
                .filteredOn(role -> !role.administrator())
                .containsExactlyInAnyOrder(
                        TechnicalRole.PLATFORM_AUDITOR,
                        TechnicalRole.ORG_AUDITOR,
                        TechnicalRole.SPACE_AUDITOR);
    }

    @Test
    void deprecatedRoles_remainResolvableButOutsideCanonicalCatalog() throws NoSuchFieldException {
        Set<TechnicalRole> deprecated = EnumSet.of(
                TechnicalRole.SELF,
                TechnicalRole.ORG_VIEWER,
                TechnicalRole.SPACE_VIEWER);

        assertThat(Arrays.stream(TechnicalRole.values()).filter(TechnicalRole::deprecated))
                .containsExactlyInAnyOrderElementsOf(deprecated);
        assertThat(TechnicalRole.canonicalValues()).doesNotContainAnyElementsOf(deprecated);

        for (TechnicalRole role : deprecated) {
            assertThat(TechnicalRole.fromCode(role.code())).contains(role);
            assertThat(TechnicalRole.class.getField(role.name()).isAnnotationPresent(Deprecated.class))
                    .as(role.code())
                    .isTrue();
        }
    }

    @Test
    void canonicalPermissions_areCompleteUniqueAndPartitionedByPlan() {
        List<TechnicalPermission> permissions = catalog.getCanonicalPermissions();

        assertThat(permissions).hasSize(45);
        assertThat(permissions)
                .filteredOn(permission -> permission.plan() == AuthorityPlan.PLATFORM)
                .hasSize(8);
        assertThat(permissions)
                .filteredOn(permission -> permission.plan() == AuthorityPlan.ORGANIZATION)
                .hasSize(22);
        assertThat(permissions)
                .filteredOn(permission -> permission.plan() == AuthorityPlan.SPACE)
                .hasSize(15);
        assertThat(permissions).extracting(TechnicalPermission::code).doesNotHaveDuplicates();
    }

    @Test
    void everyCanonicalPermissionNamesPlanResourceActionAndDescription() {
        assertThat(TechnicalPermission.values())
                .allSatisfy(permission -> {
                    assertThat(permission.code())
                            .startsWith(permission.plan().permissionCodePrefix());
                    assertThat(permission.plan()).isNotNull();
                    assertThat(permission.resource()).isNotNull();
                    assertThat(permission.action()).isNotNull();
                    assertThat(permission.description()).isNotBlank();
                    assertThat(TechnicalPermission.fromCode(permission.code())).contains(permission);
                });
    }
}
