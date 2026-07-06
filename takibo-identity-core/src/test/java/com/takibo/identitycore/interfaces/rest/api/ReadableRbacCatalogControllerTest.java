package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.identitycore.application.rbac.catalog.model.CatalogNature;
import com.takibo.identitycore.application.rbac.catalog.model.CatalogOrigin;
import com.takibo.identitycore.application.rbac.catalog.port.in.RbacCatalogQueryCase;
import com.takibo.identitycore.domain.catalogrbac.TechnicalScope;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.response.GroupCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.PermissionCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.RbacCatalogListResponse;
import com.takibo.identitycore.interfaces.rest.response.RoleCatalogResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadableRbacCatalogControllerTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final ResolvedSpaceKey KEY = new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock
    private SpaceKeyResolutionCase spaceKeyResolution;

    @Mock
    private RbacCatalogQueryCase rbacCatalogQueryCase;

    @InjectMocks
    private ReadableRbacCatalogController controller;

    @Test
    void listRoles_resolvesReadableKeyAndDelegates() {
        RbacCatalogListResponse<RoleCatalogResponse> expected = new RbacCatalogListResponse<>(List.of(), 0);
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(rbacCatalogQueryCase.listRoles(KEY)).thenReturn(expected);

        ResponseEntity<RbacCatalogListResponse<RoleCatalogResponse>> response =
                controller.listRoles("takibo-iam", "finance");

        assertThat(response.getBody()).isSameAs(expected);
        verify(rbacCatalogQueryCase).listRoles(KEY);
    }

    @Test
    void getRole_resolvesReadableKeyAndDelegatesCode() {
        RoleCatalogResponse expected = new RoleCatalogResponse(
                "R_SPACE_ADMIN", "SPACE_ADMIN", null, CatalogOrigin.TECHNICAL, CatalogNature.TECHNICAL,
                TechnicalScope.SPACE, false, true, List.of("P_MANAGE_USERS"));
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(rbacCatalogQueryCase.getRole(KEY, "R_SPACE_ADMIN")).thenReturn(expected);

        ResponseEntity<RoleCatalogResponse> response =
                controller.getRole("takibo-iam", "finance", "R_SPACE_ADMIN");

        assertThat(response.getBody()).isSameAs(expected);
        verify(rbacCatalogQueryCase).getRole(KEY, "R_SPACE_ADMIN");
    }

    @Test
    void listGroups_resolvesReadableKeyAndDelegates() {
        RbacCatalogListResponse<GroupCatalogResponse> expected = new RbacCatalogListResponse<>(List.of(), 0);
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(rbacCatalogQueryCase.listGroups(KEY)).thenReturn(expected);

        ResponseEntity<RbacCatalogListResponse<GroupCatalogResponse>> response =
                controller.listGroups("takibo-iam", "finance");

        assertThat(response.getBody()).isSameAs(expected);
        verify(rbacCatalogQueryCase).listGroups(KEY);
    }

    @Test
    void getGroup_resolvesReadableKeyAndDelegatesCode() {
        GroupCatalogResponse expected = new GroupCatalogResponse(
                "G_SPACE_ADMINS", "SPACE_ADMINS", null, CatalogOrigin.TECHNICAL, CatalogNature.TECHNICAL,
                TechnicalScope.SPACE, false, List.of("R_SPACE_ADMIN"));
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(rbacCatalogQueryCase.getGroup(KEY, "G_SPACE_ADMINS")).thenReturn(expected);

        ResponseEntity<GroupCatalogResponse> response =
                controller.getGroup("takibo-iam", "finance", "G_SPACE_ADMINS");

        assertThat(response.getBody()).isSameAs(expected);
        verify(rbacCatalogQueryCase).getGroup(KEY, "G_SPACE_ADMINS");
    }

    @Test
    void listPermissions_resolvesReadableKeyAndDelegates() {
        RbacCatalogListResponse<PermissionCatalogResponse> expected = new RbacCatalogListResponse<>(List.of(), 0);
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(rbacCatalogQueryCase.listPermissions(KEY)).thenReturn(expected);

        ResponseEntity<RbacCatalogListResponse<PermissionCatalogResponse>> response =
                controller.listPermissions("takibo-iam", "finance");

        assertThat(response.getBody()).isSameAs(expected);
        verify(rbacCatalogQueryCase).listPermissions(KEY);
    }

    @Test
    void getPermission_resolvesReadableKeyAndDelegatesCode() {
        PermissionCatalogResponse expected = new PermissionCatalogResponse(
                "P_MANAGE_USERS", "Manage users", CatalogOrigin.TECHNICAL, CatalogNature.TECHNICAL,
                TechnicalScope.ORGANIZATION, false);
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(rbacCatalogQueryCase.getPermission(KEY, "P_MANAGE_USERS")).thenReturn(expected);

        ResponseEntity<PermissionCatalogResponse> response =
                controller.getPermission("takibo-iam", "finance", "P_MANAGE_USERS");

        assertThat(response.getBody()).isSameAs(expected);
        verify(rbacCatalogQueryCase).getPermission(KEY, "P_MANAGE_USERS");
    }
}
