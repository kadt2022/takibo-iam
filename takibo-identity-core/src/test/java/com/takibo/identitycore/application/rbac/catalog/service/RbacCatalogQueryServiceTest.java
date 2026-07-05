package com.takibo.identitycore.application.rbac.catalog.service;

import com.takibo.identitycore.application.rbac.catalog.model.CatalogNature;
import com.takibo.identitycore.application.rbac.catalog.model.CatalogOrigin;
import com.takibo.identitycore.domain.catalogrbac.TechnicalScope;
import com.takibo.identitycore.domain.exception.GroupNotFoundException;
import com.takibo.identitycore.domain.exception.PermissionNotFoundException;
import com.takibo.identitycore.domain.exception.RoleNotFoundException;
import com.takibo.identitycore.domain.exception.SpaceNotActiveException;
import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.model.GroupNature;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.domain.repository.GroupRepository;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.GroupCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.PermissionCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.RbacCatalogListResponse;
import com.takibo.identitycore.interfaces.rest.response.RoleCatalogResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacCatalogQueryServiceTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final ResolvedSpaceKey KEY =
            new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock private SpaceContextVerifier spaceContextVerifier;
    @Mock private SpaceBoundaryGuard spaceBoundaryGuard;
    @Mock private RoleRepository roleRepository;
    @Mock private GroupRepository groupRepository;

    @InjectMocks
    private RbacCatalogQueryService service;

    private Role dbRole(String code, RoleNature nature) {
        Instant now = Instant.now();
        return Role.builder()
                .id(RoleId.generate())
                .spaceId(SpaceId.of(SPACE_ID))
                .code(code)
                .name(code)
                .description("db role")
                .nature(nature)
                .createdAt(now)
                .updatedAt(now)
                .version(0L)
                .build();
    }

    private Group dbGroup(String code, GroupNature nature) {
        return Group.createNew(SpaceId.of(SPACE_ID), code, code, "db group", nature);
    }

    // ─────────────────────────── roles ───────────────────────────

    @Test
    void listRoles_mergesTechnicalCatalogAndTenantRoles_sortedByCode() {
        when(roleRepository.findAllByOrgAndSpace(ORG_ID, SPACE_ID))
                .thenReturn(List.of(dbRole("B_APPROVER", RoleNature.BUSINESS)));

        RbacCatalogListResponse<RoleCatalogResponse> result = service.listRoles(KEY);

        List<String> codes = result.items().stream().map(RoleCatalogResponse::code).toList();
        // 10 rôles techniques visibles tenant (6 ORGANIZATION + 4 SPACE) + 1 rôle DB.
        assertThat(result.total()).isEqualTo(11);
        assertThat(codes)
                .contains("R_ORG_OWNER", "R_SPACE_ADMIN", "B_APPROVER")
                .isSorted();

        verify(spaceContextVerifier).validateSpaceContext(SPACE_ID);
        verify(spaceBoundaryGuard).assertTokenMatches(KEY);
    }

    @Test
    void listRoles_neverExposesSystemOrUserScopedRoles() {
        when(roleRepository.findAllByOrgAndSpace(ORG_ID, SPACE_ID)).thenReturn(List.of());

        RbacCatalogListResponse<RoleCatalogResponse> result = service.listRoles(KEY);

        List<String> codes = result.items().stream().map(RoleCatalogResponse::code).toList();
        // isNotEmpty d'abord : doesNotContain passerait trivialement sur un catalogue vide.
        assertThat(codes)
                .isNotEmpty()
                .doesNotContain("R_TAKIBO_PLATFORM_ADMIN", "R_TAKIBO_PLATFORM_AUDITOR", "R_SELF");
    }

    @Test
    void listRoles_technicalCatalogWins_onCodeCollision() {
        // Une ligne tenant ne peut pas maquiller un rôle plateforme.
        when(roleRepository.findAllByOrgAndSpace(ORG_ID, SPACE_ID))
                .thenReturn(List.of(dbRole("R_SPACE_ADMIN", RoleNature.GOVERNANCE)));

        RbacCatalogListResponse<RoleCatalogResponse> result = service.listRoles(KEY);

        RoleCatalogResponse spaceAdmin = result.items().stream()
                .filter(r -> r.code().equals("R_SPACE_ADMIN"))
                .findFirst().orElseThrow();
        assertThat(spaceAdmin.origin()).isEqualTo(CatalogOrigin.TECHNICAL);
        assertThat(spaceAdmin.nature()).isEqualTo(CatalogNature.TECHNICAL);
    }

    @Test
    void getRole_technical_isNotEditableButAssignable_withPermissions() {
        RoleCatalogResponse role = service.getRole(KEY, "R_SPACE_ADMIN");

        assertThat(role.origin()).isEqualTo(CatalogOrigin.TECHNICAL);
        assertThat(role.nature()).isEqualTo(CatalogNature.TECHNICAL);
        assertThat(role.scope()).isEqualTo(TechnicalScope.SPACE);
        assertThat(role.editable()).isFalse();
        assertThat(role.assignable()).isTrue();
        assertThat(role.permissions()).contains("P_MANAGE_USERS", "P_ASSIGN_ROLES");
        verifyNoInteractions(roleRepository);
    }

    @Test
    void getRole_database_isEditableNotAssignable() {
        when(roleRepository.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), "B_APPROVER"))
                .thenReturn(Optional.of(dbRole("B_APPROVER", RoleNature.BUSINESS)));

        RoleCatalogResponse role = service.getRole(KEY, "B_APPROVER");

        assertThat(role.origin()).isEqualTo(CatalogOrigin.DATABASE);
        assertThat(role.nature()).isEqualTo(CatalogNature.BUSINESS);
        assertThat(role.editable()).isTrue();
        assertThat(role.assignable()).isFalse();
        assertThat(role.permissions()).isEmpty();
    }

    @Test
    void getRole_platformRole_doesNotExistForTenants_evenWithSeededDbRow() {
        // Jamais de fallback DB pour un code technique caché : même si une ligne
        // seedée R_TAKIBO_PLATFORM_ADMIN traîne en base, elle n'est pas consultée.
        assertThatThrownBy(() -> service.getRole(KEY, "R_TAKIBO_PLATFORM_ADMIN"))
                .isInstanceOf(RoleNotFoundException.class);

        verifyNoInteractions(roleRepository);
    }

    @Test
    void getRole_selfRole_isNotExposedInPr25() {
        assertThatThrownBy(() -> service.getRole(KEY, "R_SELF"))
                .isInstanceOf(RoleNotFoundException.class);

        verifyNoInteractions(roleRepository);
    }

    @Test
    void listRoles_filtersSeededHiddenTechnicalRowsFromDatabase() {
        // Les migrations seedent des lignes roles avec des codes techniques cachés
        // (R_TAKIBO_PLATFORM_*, R_SELF) : elles ne doivent jamais fuir dans le catalogue.
        when(roleRepository.findAllByOrgAndSpace(ORG_ID, SPACE_ID)).thenReturn(List.of(
                dbRole("R_TAKIBO_PLATFORM_ADMIN", RoleNature.GOVERNANCE),
                dbRole("R_SELF", RoleNature.GOVERNANCE),
                dbRole("B_APPROVER", RoleNature.BUSINESS)));

        RbacCatalogListResponse<RoleCatalogResponse> result = service.listRoles(KEY);

        List<String> codes = result.items().stream().map(RoleCatalogResponse::code).toList();
        assertThat(codes)
                .contains("B_APPROVER")
                .doesNotContain("R_TAKIBO_PLATFORM_ADMIN", "R_TAKIBO_PLATFORM_AUDITOR", "R_SELF");
    }

    @Test
    void getRole_unknownCode_isNotFound() {
        when(roleRepository.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), "NOPE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRole(KEY, "NOPE"))
                .isInstanceOf(RoleNotFoundException.class);
    }

    // ─────────────────────────── groups ───────────────────────────

    @Test
    void listGroups_mergesTechnicalCatalogAndTenantGroups() {
        when(groupRepository.findAllByOrgAndSpace(ORG_ID, SPACE_ID))
                .thenReturn(List.of(dbGroup("GRP_FINANCE", GroupNature.BUSINESS)));

        RbacCatalogListResponse<GroupCatalogResponse> result = service.listGroups(KEY);

        List<String> codes = result.items().stream().map(GroupCatalogResponse::code).toList();
        // 6 groupes techniques (tous ORGANIZATION/SPACE) + 1 groupe DB.
        assertThat(result.total()).isEqualTo(7);
        assertThat(codes)
                .contains("G_ORG_ADMINS", "G_SPACE_ADMINS", "GRP_FINANCE")
                .isSorted();
    }

    @Test
    void getGroup_technical_exposesItsRoleCodes() {
        GroupCatalogResponse group = service.getGroup(KEY, "G_ORG_ADMINS");

        assertThat(group.origin()).isEqualTo(CatalogOrigin.TECHNICAL);
        assertThat(group.nature()).isEqualTo(CatalogNature.TECHNICAL);
        assertThat(group.editable()).isFalse();
        assertThat(group.roles()).containsExactly("R_ORG_ADMIN", "R_ORG_OWNER");
        verifyNoInteractions(groupRepository);
    }

    @Test
    void getGroup_database_isEditable() {
        when(groupRepository.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), "GRP_FINANCE"))
                .thenReturn(Optional.of(dbGroup("GRP_FINANCE", GroupNature.BUSINESS)));

        GroupCatalogResponse group = service.getGroup(KEY, "GRP_FINANCE");

        assertThat(group.origin()).isEqualTo(CatalogOrigin.DATABASE);
        assertThat(group.nature()).isEqualTo(CatalogNature.BUSINESS);
        assertThat(group.editable()).isTrue();
    }

    @Test
    void getGroup_unknownCode_isNotFound() {
        when(groupRepository.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), "NOPE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGroup(KEY, "NOPE"))
                .isInstanceOf(GroupNotFoundException.class);
    }

    // ─────────────────────────── permissions ───────────────────────────

    @Test
    void listPermissions_exposesTechnicalPermissions_withoutSystemScope() {
        RbacCatalogListResponse<PermissionCatalogResponse> result = service.listPermissions(KEY);

        List<String> codes = result.items().stream().map(PermissionCatalogResponse::code).toList();
        // 13 permissions techniques - 2 SYSTEM (P_CREATE_ORG, P_DELETE_ORG).
        assertThat(result.total()).isEqualTo(11);
        assertThat(codes)
                .contains("P_MANAGE_USERS", "P_ASSIGN_ROLES", "P_READ_ORG")
                .doesNotContain("P_CREATE_ORG", "P_DELETE_ORG")
                .isSorted();
    }

    @Test
    void getPermission_technical_returnsDetails() {
        PermissionCatalogResponse permission = service.getPermission(KEY, "P_MANAGE_USERS");

        assertThat(permission.origin()).isEqualTo(CatalogOrigin.TECHNICAL);
        assertThat(permission.nature()).isEqualTo(CatalogNature.TECHNICAL);
        assertThat(permission.editable()).isFalse();
        assertThat(permission.description()).isNotBlank();
    }

    @Test
    void getPermission_systemScope_doesNotExistForTenants() {
        assertThatThrownBy(() -> service.getPermission(KEY, "P_CREATE_ORG"))
                .isInstanceOf(PermissionNotFoundException.class);
    }

    @Test
    void getPermission_unknownCode_isNotFound() {
        assertThatThrownBy(() -> service.getPermission(KEY, "P_NOPE"))
                .isInstanceOf(PermissionNotFoundException.class);
    }

    // ─────────────────────────── frontière ───────────────────────────

    @Test
    void spaceInactive_denied_beforeAnyQuery() {
        doThrow(new SpaceNotActiveException(SPACE_ID))
                .when(spaceContextVerifier).validateSpaceContext(SPACE_ID);

        assertThatThrownBy(() -> service.listRoles(KEY))
                .isInstanceOf(SpaceNotActiveException.class);

        verifyNoInteractions(roleRepository, groupRepository);
    }

    @Test
    void tokenOutsideBoundary_denied_beforeAnyQuery() {
        doThrow(new AccessDeniedException("SPACE_CONTEXT_MISMATCH"))
                .when(spaceBoundaryGuard).assertTokenMatches(KEY);

        assertThatThrownBy(() -> service.listGroups(KEY))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("SPACE_CONTEXT_MISMATCH");

        verifyNoInteractions(roleRepository, groupRepository);
    }
}
