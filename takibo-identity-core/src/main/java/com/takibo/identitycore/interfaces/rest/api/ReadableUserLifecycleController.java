package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.audit.annotations.Audit;
import com.takibo.audit.annotations.LogAction;
import com.takibo.audit.domain.AuditType;
import com.takibo.identitycore.application.identity.command.ChangeUserStatusCommand;
import com.takibo.identitycore.application.identity.command.UpdateUserProfileCommand;
import com.takibo.identitycore.application.identity.port.UserLifecycleCase;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.request.UpdateUserProfileRequest;
import com.takibo.identitycore.interfaces.rest.request.UserStatusChangeRequest;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Cycle de vie du user local, sur la surface lisible. Pas de suppression physique :
 * désactiver ferme la porte locale sans effacer la trace. Le {@code reason} est
 * une justification d'audit, jamais un attribut du user.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgCode}/spaces/{spaceCode}/users/{userId}")
@RequiredArgsConstructor
@Validated
public class ReadableUserLifecycleController {

    private final SpaceKeyResolutionCase spaceKeyResolution;
    private final UserLifecycleCase userLifecycleCase;

    @PatchMapping
    @LogAction("Update local user profile (readable route)")
    @Audit(type = AuditType.UPDATE, entityType = "USER", entityIdParam = "result.body.id")
    @Operation(summary = "Update the local profile of a user (username, names, metadata)")
    @ApiResponse(responseCode = "200", description = "Profile updated")
    @ApiResponse(responseCode = "404", description = "Organization, space or user not found")
    @ApiResponse(responseCode = "409", description = "Username already exists in this space")
    public ResponseEntity<UserResponse> updateProfile(@PathVariable("orgCode") String orgCode,
                                                      @PathVariable("spaceCode") String spaceCode,
                                                      @PathVariable("userId") UUID userId,
                                                      @Valid @RequestBody UpdateUserProfileRequest request) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
                userId, request.username(), request.firstName(), request.lastName(), request.metadata());
        return ResponseEntity.ok(userLifecycleCase.updateProfile(key, command));
    }

    @PostMapping("/suspend")
    @LogAction("Suspend user (readable route)")
    @Audit(type = AuditType.SECURITY, entityType = "USER", entityIdParam = "result.body.id")
    @Operation(summary = "Suspend a user (ACTIVE -> SUSPENDED)")
    @ApiResponse(responseCode = "200", description = "User suspended")
    @ApiResponse(responseCode = "409", description = "Status transition not allowed")
    public ResponseEntity<UserResponse> suspend(@PathVariable("orgCode") String orgCode,
                                                @PathVariable("spaceCode") String spaceCode,
                                                @PathVariable("userId") UUID userId,
                                                @Valid @RequestBody(required = false) UserStatusChangeRequest request) {
        return changeStatus(orgCode, spaceCode, userId, UserStatus.SUSPENDED, request);
    }

    @PostMapping("/activate")
    @LogAction("Activate user (readable route)")
    @Audit(type = AuditType.SECURITY, entityType = "USER", entityIdParam = "result.body.id")
    @Operation(summary = "Activate a user (SUSPENDED/LOCKED/PASSWORD_RESET/PENDING_ACTIVATION -> ACTIVE)")
    @ApiResponse(responseCode = "200", description = "User activated")
    @ApiResponse(responseCode = "409", description = "Status transition not allowed")
    public ResponseEntity<UserResponse> activate(@PathVariable("orgCode") String orgCode,
                                                 @PathVariable("spaceCode") String spaceCode,
                                                 @PathVariable("userId") UUID userId,
                                                 @Valid @RequestBody(required = false) UserStatusChangeRequest request) {
        return changeStatus(orgCode, spaceCode, userId, UserStatus.ACTIVE, request);
    }

    @PostMapping("/lock")
    @LogAction("Lock user (readable route)")
    @Audit(type = AuditType.SECURITY, entityType = "USER", entityIdParam = "result.body.id")
    @Operation(summary = "Lock a user (ACTIVE -> LOCKED)")
    @ApiResponse(responseCode = "200", description = "User locked")
    @ApiResponse(responseCode = "409", description = "Status transition not allowed")
    public ResponseEntity<UserResponse> lock(@PathVariable("orgCode") String orgCode,
                                             @PathVariable("spaceCode") String spaceCode,
                                             @PathVariable("userId") UUID userId,
                                             @Valid @RequestBody(required = false) UserStatusChangeRequest request) {
        return changeStatus(orgCode, spaceCode, userId, UserStatus.LOCKED, request);
    }

    @PostMapping("/deactivate")
    @LogAction("Deactivate user (readable route)")
    @Audit(type = AuditType.SECURITY, entityType = "USER", entityIdParam = "result.body.id")
    @Operation(summary = "Deactivate a user (terminal — keeps the trace, no physical delete)")
    @ApiResponse(responseCode = "200", description = "User deactivated")
    @ApiResponse(responseCode = "409", description = "Status transition not allowed")
    public ResponseEntity<UserResponse> deactivate(@PathVariable("orgCode") String orgCode,
                                                   @PathVariable("spaceCode") String spaceCode,
                                                   @PathVariable("userId") UUID userId,
                                                   @Valid @RequestBody(required = false) UserStatusChangeRequest request) {
        return changeStatus(orgCode, spaceCode, userId, UserStatus.DEACTIVATED, request);
    }

    private ResponseEntity<UserResponse> changeStatus(String orgCode, String spaceCode, UUID userId,
                                                      UserStatus target, UserStatusChangeRequest request) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        ChangeUserStatusCommand command = new ChangeUserStatusCommand(
                userId, target, request != null ? request.reason() : null);
        return ResponseEntity.ok(userLifecycleCase.changeStatus(key, command));
    }
}
