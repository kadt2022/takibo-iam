package com.takibo.identitycore.application.rbac.catalog.port.in;

import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.GroupCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.PermissionCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.RbacCatalogListResponse;
import com.takibo.identitycore.interfaces.rest.response.RoleCatalogResponse;

/**
 * Read-side du catalogue RBAC situé d'un space. Lecture seulement :
 * aucune création, modification, suppression ni assignation.
 */
public interface RbacCatalogQueryCase {

    RbacCatalogListResponse<RoleCatalogResponse> listRoles(ResolvedSpaceKey key);

    RoleCatalogResponse getRole(ResolvedSpaceKey key, String roleCode);

    RbacCatalogListResponse<GroupCatalogResponse> listGroups(ResolvedSpaceKey key);

    GroupCatalogResponse getGroup(ResolvedSpaceKey key, String groupCode);

    RbacCatalogListResponse<PermissionCatalogResponse> listPermissions(ResolvedSpaceKey key);

    PermissionCatalogResponse getPermission(ResolvedSpaceKey key, String permissionCode);
}
