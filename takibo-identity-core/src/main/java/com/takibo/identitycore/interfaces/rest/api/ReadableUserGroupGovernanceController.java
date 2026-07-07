package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.audit.annotations.Audit;
import com.takibo.audit.annotations.LogAction;
import com.takibo.audit.domain.AuditType;
import com.takibo.identitycore.application.rbac.governance.command.AddUserToGroupCommand;
import com.takibo.identitycore.application.rbac.governance.command.RemoveUserFromGroupCommand;
import com.takibo.identitycore.application.rbac.governance.port.in.UserGroupGovernanceCase;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.request.AddUserToGroupRequest;
import com.takibo.identitycore.interfaces.rest.response.UserGroupMembershipsResponse;
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
 * Memberships directs d'un user, sur la surface lisible. Le groupe transmet le
 * pouvoir par appartenance : mêmes gardes que la délégation de rôle, même
 * idempotence (l'état courant est toujours retourné).
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgCode}/spaces/{spaceCode}/users/{userId}/groups")
@RequiredArgsConstructor
@Validated
public class ReadableUserGroupGovernanceController {

    private final SpaceKeyResolutionCase spaceKeyResolution;
    private final UserGroupGovernanceCase userGroupGovernanceCase;

    @GetMapping
    @LogAction("List direct groups of a user (readable route)")
    @Operation(summary = "List the direct group memberships of a user in this space")
    @ApiResponse(responseCode = "200", description = "Direct group memberships",
            content = @Content(schema = @Schema(implementation = UserGroupMembershipsResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token not scoped to this space or missing admin role")
    @ApiResponse(responseCode = "404", description = "Organization, space or user not found")
    public ResponseEntity<UserGroupMembershipsResponse> list(@PathVariable("orgCode") String orgCode,
                                                             @PathVariable("spaceCode") String spaceCode,
                                                             @PathVariable("userId") UUID userId) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        return ResponseEntity.ok(userGroupGovernanceCase.listDirectGroups(key, userId));
    }

    @PostMapping
    @LogAction("Add a user to a group (readable route)")
    @Audit(type = AuditType.SECURITY, entityType = "USER", entityIdParam = "result.body.userId")
    @Operation(summary = "Add a user to a technical or governance group (idempotent)")
    @ApiResponse(responseCode = "200", description = "Current memberships after the operation",
            content = @Content(schema = @Schema(implementation = UserGroupMembershipsResponse.class)))
    @ApiResponse(responseCode = "403", description = "Missing admin role, business group or scope escalation")
    @ApiResponse(responseCode = "404", description = "Organization, space, user or group not found")
    public ResponseEntity<UserGroupMembershipsResponse> add(@PathVariable("orgCode") String orgCode,
                                                            @PathVariable("spaceCode") String spaceCode,
                                                            @PathVariable("userId") UUID userId,
                                                            @Valid @RequestBody AddUserToGroupRequest request) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        AddUserToGroupCommand command = new AddUserToGroupCommand(userId, request.groupCode(), request.reason());
        return ResponseEntity.ok(userGroupGovernanceCase.addToGroup(key, command));
    }

    @DeleteMapping("/{groupCode}")
    @LogAction("Remove a user from a group (readable route)")
    @Audit(type = AuditType.SECURITY, entityType = "USER", entityIdParam = "result.body.userId")
    @Operation(summary = "Remove a user from a group (idempotent, direct membership only)")
    @ApiResponse(responseCode = "200", description = "Current memberships after the operation",
            content = @Content(schema = @Schema(implementation = UserGroupMembershipsResponse.class)))
    @ApiResponse(responseCode = "403", description = "Missing admin role, business group or scope escalation")
    @ApiResponse(responseCode = "404", description = "Organization, space, user or group not found")
    @ApiResponse(responseCode = "409", description = "Last space admin group member or self-demotion denied")
    public ResponseEntity<UserGroupMembershipsResponse> remove(@PathVariable("orgCode") String orgCode,
                                                               @PathVariable("spaceCode") String spaceCode,
                                                               @PathVariable("userId") UUID userId,
                                                               @PathVariable("groupCode") String groupCode) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        RemoveUserFromGroupCommand command = new RemoveUserFromGroupCommand(userId, groupCode, null);
        return ResponseEntity.ok(userGroupGovernanceCase.removeFromGroup(key, command));
    }
}
