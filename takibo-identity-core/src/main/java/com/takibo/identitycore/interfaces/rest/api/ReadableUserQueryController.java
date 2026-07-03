package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.audit.annotations.LogAction;
import com.takibo.identitycore.application.identity.port.UserQueryCase;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.response.UserPageResponse;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-side lisible des users d'un space. Même frontière que la création :
 * résolution TMS des codes, token situé exigé, rôle admin tenant exigé (policy).
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgCode}/spaces/{spaceCode}/users")
@RequiredArgsConstructor
@Validated
public class ReadableUserQueryController {

    private final SpaceKeyResolutionCase spaceKeyResolution;
    private final UserQueryCase userQueryCase;

    @GetMapping
    @LogAction("List users of a space (readable route)")
    @Operation(summary = "List users of a space via human-readable org/space codes")
    @ApiResponse(responseCode = "200", description = "Page of users",
            content = @Content(schema = @Schema(implementation = UserPageResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token not scoped to this space or missing admin role")
    @ApiResponse(responseCode = "404", description = "Organization or space not found")
    public ResponseEntity<UserPageResponse> list(@PathVariable("orgCode") String orgCode,
                                                 @PathVariable("spaceCode") String spaceCode,
                                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                                 @RequestParam(value = "status", required = false) UserStatus status,
                                                 @RequestParam(value = "type", required = false) UserType type,
                                                 @RequestParam(value = "q", required = false) String q) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        return ResponseEntity.ok(userQueryCase.listUsers(key, status, type, q, page, size));
    }

    @GetMapping("/{userId}")
    @LogAction("Read a user of a space (readable route)")
    @Operation(summary = "Read a single user of a space via human-readable org/space codes")
    @ApiResponse(responseCode = "200", description = "User details",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token not scoped to this space or missing admin role")
    @ApiResponse(responseCode = "404", description = "Organization, space or user not found")
    public ResponseEntity<UserResponse> get(@PathVariable("orgCode") String orgCode,
                                            @PathVariable("spaceCode") String spaceCode,
                                            @PathVariable("userId") UUID userId) {
        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);
        return ResponseEntity.ok(userQueryCase.getUser(key, userId));
    }
}
