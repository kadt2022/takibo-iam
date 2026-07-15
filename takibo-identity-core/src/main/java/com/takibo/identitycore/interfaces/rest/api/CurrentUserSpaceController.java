package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.audit.annotations.LogAction;
import com.takibo.identitycore.application.spacecontext.port.in.CurrentUserSpaceQueryCase;
import com.takibo.identitycore.interfaces.rest.response.CurrentUserSpacesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/spaces")
@RequiredArgsConstructor
public class CurrentUserSpaceController {

    private final CurrentUserSpaceQueryCase currentUserSpaceQueryCase;

    @GetMapping
    @LogAction("List current user's accessible space contexts")
    @Operation(summary = "List Space contexts accessible to the current organization-scoped human account")
    @ApiResponse(responseCode = "200", description = "Accessible Space contexts",
            content = @Content(schema = @Schema(implementation = CurrentUserSpacesResponse.class)))
    @ApiResponse(responseCode = "403", description = "Organization-scoped human account token required")
    public ResponseEntity<CurrentUserSpacesResponse> listCurrentUserSpaces() {
        return ResponseEntity.ok(currentUserSpaceQueryCase.listAccessibleSpaces());
    }
}
