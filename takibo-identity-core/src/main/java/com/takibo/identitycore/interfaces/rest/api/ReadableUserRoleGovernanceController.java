package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.audit.annotations.Audit;
import com.takibo.audit.annotations.LogAction;
import com.takibo.audit.domain.AuditType;
import com.takibo.identitycore.application.rbac.governance.command.AssignUserRoleCommand;
import com.takibo.identitycore.application.rbac.governance.command.RemoveUserRoleCommand;
import com.takibo.identitycore.application.rbac.governance.port.in.UserRoleGovernanceCase;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.request.AssignUserRoleRequest;
import com.takibo.identitycore.interfaces.rest.response.UserRoleAssignmentsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Délégation des rôles directs d'un user, sur la surface lisible. Lecture et
 * mutation exigent un rôle admin tenant (policy TSM) dans la frontière stricte du
 * token situé. Toutes les opérations retournent l'état courant (idempotence).
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgCode}/spaces/{spaceCode}/users/{userId}/roles")
@RequiredArgsConstructor
@Validated
public class ReadableUserRoleGovernanceController {

    private final SpaceKeyResolutionCase spaceKeyResolution;
    private final UserRoleGovernanceCase userRoleGovernanceCase;

    @GetMapping
    @LogAction("List direct roles of a user (readable route)")
    @Operation(summary = "List the direct role assignments of a user in this space")
    @ApiResponse(responseCode = "200", description = "Direct role assignments",
            content = @Content(schema = @Schema(implementation = UserRoleAssignmentsResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token not scoped to this space or missing admin role")
    @ApiResponse(responseCode = "404", description = "Organization, space or user not found")
    public ResponseEntity<UserRoleAssignmentsResponse> list(@PathVariable("orgCode") String orgCode,
                                                            @PathVariable("spaceCode") String spaceCode,
                                                            @PathVariable("userId") UUID userId) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        return ResponseEntity.ok(userRoleGovernanceCase.listDirectRoles(key, userId));
    }

    @PostMapping
    @LogAction("Assign a role to a user (readable route)")
    @Audit(type = AuditType.SECURITY, entityType = "USER", entityIdParam = "result.body.userId")
    @Operation(summary = "Assign a technical or governance role to a user (idempotent)")
    @ApiResponse(responseCode = "200", description = "Current direct assignments after the operation",
            content = @Content(schema = @Schema(implementation = UserRoleAssignmentsResponse.class)))
    @ApiResponse(responseCode = "400", description = "Reserved tenant role code")
    @ApiResponse(responseCode = "403", description = "Missing admin role, business role or scope escalation")
    @ApiResponse(responseCode = "404", description = "Organization, space, user or role not found")
    public ResponseEntity<UserRoleAssignmentsResponse> assign(@PathVariable("orgCode") String orgCode,
                                                              @PathVariable("spaceCode") String spaceCode,
                                                              @PathVariable("userId") UUID userId,
                                                              @Valid @RequestBody AssignUserRoleRequest request) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        AssignUserRoleCommand command = new AssignUserRoleCommand(userId, request.roleCode(), request.reason());
        return ResponseEntity.ok(userRoleGovernanceCase.assignRole(key, command));
    }

    @DeleteMapping("/{roleCode}")
    @LogAction("Remove a direct role from a user (readable route)")
    @Audit(type = AuditType.SECURITY, entityType = "USER", entityIdParam = "result.body.userId")
    @Operation(summary = "Remove a direct role assignment from a user (idempotent, direct only)")
    @ApiResponse(responseCode = "200", description = "Current direct assignments after the operation",
            content = @Content(schema = @Schema(implementation = UserRoleAssignmentsResponse.class)))
    @ApiResponse(responseCode = "403", description = "Missing admin role, business role or scope escalation")
    @ApiResponse(responseCode = "404", description = "Organization, space, user or role not found")
    @ApiResponse(responseCode = "409", description = "Last space admin or self-demotion denied")
    public ResponseEntity<UserRoleAssignmentsResponse> remove(@PathVariable("orgCode") String orgCode,
                                                              @PathVariable("spaceCode") String spaceCode,
                                                              @PathVariable("userId") UUID userId,
                                                              @PathVariable("roleCode") String roleCode) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        RemoveUserRoleCommand command = new RemoveUserRoleCommand(userId, roleCode, null);
        return ResponseEntity.ok(userRoleGovernanceCase.removeRole(key, command));
    }
}
