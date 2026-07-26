package com.takibo.identitycore.application.rbac.effective.service;

import com.takibo.identitycore.application.rbac.effective.model.EffectivePermissionRequest;
import com.takibo.identitycore.application.rbac.effective.model.PermissionCode;
import com.takibo.identitycore.application.rbac.effective.model.RbacActorSource;
import com.takibo.identitycore.application.rbac.effective.model.RbacSubjectNature;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.takibo.identitycore.domain.catalogrbac.AuthorityPlan.ORGANIZATION;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityPlan.PLATFORM;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityPlan.SPACE;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalPermission.*;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation")
class EffectivePermissionResolverTest {

    private static final UUID ORG_ID =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORG_ID =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000099");
    private static final UUID SPACE_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID OTHER_SPACE_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000099");

    private final RolePermissionCatalog rolePermissionCatalog = new RolePermissionCatalog();

    @Mock
    private SpaceManagementCase spaceManagementCase;

    private EffectivePermissionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EffectivePermissionResolver(rolePermissionCatalog, spaceManagementCase);
    }

    @Test
    void organizationAdminInSpace_projectsAllFifteenSpacePermissionsWithoutRewritingRole() {
        givenSpaceInOrganization(SPACE_ID, ORG_ID);
        SituatedTechnicalRole organizationAdmin =
                new SituatedTechnicalRole(ORG_ADMIN, ORG_ID, null);
        EffectivePermissionRequest request = request(
                Set.of(organizationAdmin), Set.of(), SPACE, ORG_ID, SPACE_ID);

        Set<PermissionCode> result = resolver.resolve(request);

        assertThat(result).containsExactlyInAnyOrderElementsOf(codesForPlan(SPACE));
        assertThat(request.roles()).containsExactly(organizationAdmin);
    }

    @Test
    void organizationOwnerProjection_excludesAllSevenNonProjectableOrganizationPermissions() {
        givenSpaceInOrganization(SPACE_ID, ORG_ID);
        EffectivePermissionRequest request = request(
                Set.of(new SituatedTechnicalRole(ORG_OWNER, ORG_ID, null)),
                Set.of(),
                SPACE,
                ORG_ID,
                SPACE_ID);

        Set<PermissionCode> result = resolver.resolve(request);

        assertThat(result).containsExactlyInAnyOrderElementsOf(codesForPlan(SPACE));
        assertThat(result)
                .doesNotContain(
                        code(ORG_READ),
                        code(ORG_UPDATE),
                        code(ORG_OWNERSHIP_TRANSFER),
                        code(ORG_DEACTIVATE),
                        code(ORG_DELETION_REQUEST),
                        code(ORG_SPACES_CREATE),
                        code(ORG_SPACES_DELETE));
    }

    @Test
    void spaceAdmin_neverProjectsUpToOrganization() {
        EffectivePermissionRequest request = request(
                Set.of(new SituatedTechnicalRole(SPACE_ADMIN, ORG_ID, SPACE_ID)),
                Set.of(),
                ORGANIZATION,
                ORG_ID,
                null);

        Set<PermissionCode> result = resolver.resolve(request);

        assertThat(result).isEmpty();
        verifyNoInteractions(spaceManagementCase);
    }

    @Test
    void organizationGroup_inheritsOnlyItsInheritableRole() {
        EffectivePermissionRequest request = request(
                Set.of(),
                Set.of(new SituatedTechnicalGroup(
                        TechnicalGroup.ORG_ADMINS, ORG_ID, null)),
                ORGANIZATION,
                ORG_ID,
                null);

        Set<PermissionCode> result = resolver.resolve(request);

        assertThat(result).containsExactlyInAnyOrderElementsOf(
                codes(rolePermissionCatalog.permissionsFor(ORG_ADMIN)));
        assertThat(result).doesNotContain(
                code(ORG_OWNERSHIP_TRANSFER),
                code(ORG_DEACTIVATE),
                code(ORG_DELETION_REQUEST));
    }

    @Test
    void directAndGroupRoles_areUnitedAndDeduplicatedInOneSpace() {
        givenSpaceInOrganization(SPACE_ID, ORG_ID);
        EffectivePermissionRequest request = request(
                Set.of(new SituatedTechnicalRole(SPACE_AUDITOR, ORG_ID, SPACE_ID)),
                Set.of(new SituatedTechnicalGroup(
                        TechnicalGroup.SPACE_ADMINS, ORG_ID, SPACE_ID)),
                SPACE,
                ORG_ID,
                SPACE_ID);

        Set<PermissionCode> result = resolver.resolve(request);

        assertThat(result).containsExactlyInAnyOrderElementsOf(codesForPlan(SPACE));
    }

    @Test
    void platformAdminOnPlatform_receivesOnlyTheEightPlatformPermissions() {
        EffectivePermissionRequest request = request(
                Set.of(new SituatedTechnicalRole(PLATFORM_ADMIN, null, null)),
                Set.of(),
                PLATFORM,
                null,
                null);

        Set<PermissionCode> result = resolver.resolve(request);

        assertThat(result).containsExactlyInAnyOrderElementsOf(codesForPlan(PLATFORM));
        verifyNoInteractions(spaceManagementCase);
    }

    @Test
    void deprecatedRole_isDeniedByDefault() {
        EffectivePermissionRequest request = request(
                Set.of(new SituatedTechnicalRole(ORG_VIEWER, ORG_ID, null)),
                Set.of(),
                ORGANIZATION,
                ORG_ID,
                null);

        assertThat(resolver.resolve(request)).isEmpty();
    }

    @Test
    void spaceResolutionWithoutSpaceId_isRefused() {
        EffectivePermissionRequest request =
                request(Set.of(), Set.of(), SPACE, ORG_ID, null);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(EffectivePermissionResolutionException.class)
                .hasMessageContaining("requires spaceId");
    }

    @Test
    void organizationResolutionWithoutOrgId_isRefused() {
        EffectivePermissionRequest request =
                request(Set.of(), Set.of(), ORGANIZATION, null, null);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(EffectivePermissionResolutionException.class)
                .hasMessageContaining("requires orgId");
    }

    @Test
    void spaceBelongingToAnotherOrganization_isRefused() {
        givenSpaceInOrganization(SPACE_ID, OTHER_ORG_ID);
        EffectivePermissionRequest request =
                request(Set.of(), Set.of(), SPACE, ORG_ID, SPACE_ID);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(EffectivePermissionResolutionException.class)
                .hasMessageContaining("does not belong to organization");
    }

    @Test
    void spaceRoleSituatedInAnotherSpace_isRefused() {
        givenSpaceInOrganization(SPACE_ID, ORG_ID);
        EffectivePermissionRequest request = request(
                Set.of(new SituatedTechnicalRole(
                        SPACE_ADMIN, ORG_ID, OTHER_SPACE_ID)),
                Set.of(),
                SPACE,
                ORG_ID,
                SPACE_ID);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(EffectivePermissionResolutionException.class)
                .hasMessageContaining("belongs to another space");
    }

    @Test
    void organizationRoleSituatedInAnotherOrganization_isRefused() {
        EffectivePermissionRequest request = request(
                Set.of(new SituatedTechnicalRole(
                        ORG_ADMIN, OTHER_ORG_ID, null)),
                Set.of(),
                ORGANIZATION,
                ORG_ID,
                null);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(EffectivePermissionResolutionException.class)
                .hasMessageContaining("belongs to another organization");
    }

    @Test
    void automaticPlatformProjectionToTenant_isRefused() {
        givenSpaceInOrganization(SPACE_ID, ORG_ID);
        EffectivePermissionRequest request = request(
                Set.of(new SituatedTechnicalRole(PLATFORM_ADMIN, null, null)),
                Set.of(),
                SPACE,
                ORG_ID,
                SPACE_ID);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(EffectivePermissionResolutionException.class)
                .hasMessageContaining("Automatic PLATFORM projection");
    }

    @Test
    void sameInput_producesTheSameSortedImmutablePermissionSet() {
        givenSpaceInOrganization(SPACE_ID, ORG_ID);
        EffectivePermissionRequest request = request(
                Set.of(new SituatedTechnicalRole(ORG_ADMIN, ORG_ID, null)),
                Set.of(),
                SPACE,
                ORG_ID,
                SPACE_ID);

        Set<PermissionCode> first = resolver.resolve(request);
        Set<PermissionCode> second = resolver.resolve(request);

        assertThat(second).containsExactlyElementsOf(first);
        PermissionCode extra = code(SPACE_AUDIT_EXPORT);
        assertThatThrownBy(() -> first.add(extra))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void givenSpaceInOrganization(UUID spaceId, UUID organizationId) {
        when(spaceManagementCase.findOrgIdBySpaceId(SpaceId.of(spaceId)))
                .thenReturn(java.util.Optional.of(organizationId));
    }

    private EffectivePermissionRequest request(
            Set<SituatedTechnicalRole> roles,
            Set<SituatedTechnicalGroup> groups,
            AuthorityPlan authorityPlan,
            UUID orgId,
            UUID spaceId
    ) {
        return new EffectivePermissionRequest(
                roles,
                groups,
                authorityPlan,
                orgId,
                spaceId,
                RbacSubjectNature.HUMAN,
                RbacActorSource.HUMAN);
    }

    private static Set<PermissionCode> codesForPlan(AuthorityPlan plan) {
        return Arrays.stream(TechnicalPermission.values())
                .filter(permission -> permission.plan() == plan)
                .map(EffectivePermissionResolverTest::code)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<PermissionCode> codes(Set<TechnicalPermission> permissions) {
        return permissions.stream()
                .map(EffectivePermissionResolverTest::code)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static PermissionCode code(TechnicalPermission permission) {
        return PermissionCode.from(permission);
    }
}
