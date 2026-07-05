package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.audit.annotations.LogAction;
import com.takibo.identitycore.application.rbac.catalog.port.in.RbacCatalogQueryCase;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.response.GroupCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.PermissionCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.RbacCatalogListResponse;
import com.takibo.identitycore.interfaces.rest.response.RoleCatalogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-side lisible du catalogue RBAC d'un space : rôles, groupes, permissions.
 * Lecture seulement — la surface exige un rôle admin tenant (policy TSM) et la
 * frontière stricte du token situé. Le catalogue est composite : catalogue technique
 * TAKIBO (scopes ORGANIZATION/SPACE) + éléments tenant persistés en base.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgCode}/spaces/{spaceCode}")
@RequiredArgsConstructor
@Validated
public class ReadableRbacCatalogController {

    private final SpaceKeyResolutionCase spaceKeyResolution;
    private final RbacCatalogQueryCase rbacCatalogQueryCase;

    @GetMapping("/roles")
    @LogAction("List RBAC role catalog of a space (readable route)")
    @Operation(summary = "List the role catalog of a space (technical + tenant roles)")
    @ApiResponse(responseCode = "200", description = "Role catalog",
            content = @Content(schema = @Schema(implementation = RbacCatalogListResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token not scoped to this space or missing admin role")
    @ApiResponse(responseCode = "404", description = "Organization or space not found")
    public ResponseEntity<RbacCatalogListResponse<RoleCatalogResponse>> listRoles(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("spaceCode") String spaceCode) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        return ResponseEntity.ok(rbacCatalogQueryCase.listRoles(key));
    }

    @GetMapping("/roles/{roleCode}")
    @LogAction("Read a RBAC catalog role (readable route)")
    @Operation(summary = "Read a single role of the space catalog by code")
    @ApiResponse(responseCode = "200", description = "Role details",
            content = @Content(schema = @Schema(implementation = RoleCatalogResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token not scoped to this space or missing admin role")
    @ApiResponse(responseCode = "404", description = "Organization, space or role not found")
    public ResponseEntity<RoleCatalogResponse> getRole(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("spaceCode") String spaceCode,
            @PathVariable("roleCode") String roleCode) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        return ResponseEntity.ok(rbacCatalogQueryCase.getRole(key, roleCode));
    }

    @GetMapping("/groups")
    @LogAction("List RBAC group catalog of a space (readable route)")
    @Operation(summary = "List the group catalog of a space (technical + tenant groups)")
    @ApiResponse(responseCode = "200", description = "Group catalog",
            content = @Content(schema = @Schema(implementation = RbacCatalogListResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token not scoped to this space or missing admin role")
    @ApiResponse(responseCode = "404", description = "Organization or space not found")
    public ResponseEntity<RbacCatalogListResponse<GroupCatalogResponse>> listGroups(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("spaceCode") String spaceCode) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        return ResponseEntity.ok(rbacCatalogQueryCase.listGroups(key));
    }

    @GetMapping("/groups/{groupCode}")
    @LogAction("Read a RBAC catalog group (readable route)")
    @Operation(summary = "Read a single group of the space catalog by code")
    @ApiResponse(responseCode = "200", description = "Group details",
            content = @Content(schema = @Schema(implementation = GroupCatalogResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token not scoped to this space or missing admin role")
    @ApiResponse(responseCode = "404", description = "Organization, space or group not found")
    public ResponseEntity<GroupCatalogResponse> getGroup(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("spaceCode") String spaceCode,
            @PathVariable("groupCode") String groupCode) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        return ResponseEntity.ok(rbacCatalogQueryCase.getGroup(key, groupCode));
    }

    @GetMapping("/permissions")
    @LogAction("List RBAC permission catalog of a space (readable route)")
    @Operation(summary = "List the permission catalog of a space (technical permissions)")
    @ApiResponse(responseCode = "200", description = "Permission catalog",
            content = @Content(schema = @Schema(implementation = RbacCatalogListResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token not scoped to this space or missing admin role")
    @ApiResponse(responseCode = "404", description = "Organization or space not found")
    public ResponseEntity<RbacCatalogListResponse<PermissionCatalogResponse>> listPermissions(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("spaceCode") String spaceCode) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        return ResponseEntity.ok(rbacCatalogQueryCase.listPermissions(key));
    }

    @GetMapping("/permissions/{permissionCode}")
    @LogAction("Read a RBAC catalog permission (readable route)")
    @Operation(summary = "Read a single permission of the space catalog by code")
    @ApiResponse(responseCode = "200", description = "Permission details",
            content = @Content(schema = @Schema(implementation = PermissionCatalogResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token not scoped to this space or missing admin role")
    @ApiResponse(responseCode = "404", description = "Organization, space or permission not found")
    public ResponseEntity<PermissionCatalogResponse> getPermission(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("spaceCode") String spaceCode,
            @PathVariable("permissionCode") String permissionCode) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        return ResponseEntity.ok(rbacCatalogQueryCase.getPermission(key, permissionCode));
    }
}
