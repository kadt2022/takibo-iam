// =============================================================
// Add User V1 Endpoint — Account + User (Single-call onboarding)
// =============================================================
// This patch introduces a new endpoint: POST /api/spaces/{spaceId}/users
// supporting two modes:
// - Attach existing Account: { accountId, username, ... }
// - Create new Account + Credentials then User: { email, rawPassword, username, ... }
//
// It replaces the current header-based endpoint /api/users (X-Space-Id) and
// removes email/password from the User aggregate in favor of Account(+Credentials).
//
// ─────────────────────────────────────────────────────────────────────────────
// FILE 1 — interfaces/rest/api/UserControllerV2.java
// ─────────────────────────────────────────────────────────────────────────────
package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.audit.annotations.Audit;
import com.takibo.audit.annotations.LogAction;
import com.takibo.audit.annotations.TriggerAlertOnFailure;
import com.takibo.audit.domain.AuditType;
import com.takibo.identitycore.integration.space.annotations.RequireActiveSpace;
import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.application.identity.service.UserApplicationService;
import com.takibo.identitycore.domain.exception.UserNotFoundException;
import com.takibo.identitycore.interfaces.rest.request.CreateUserRequestV2;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


import java.util.UUID;


@RestController
@RequestMapping("/api/spaces/{spaceId}/users")
@RequiredArgsConstructor
@Validated
@RequireActiveSpace
@Slf4j
public class UserController {

    private final UserApplicationService service;

    @PostMapping
    @LogAction("Registration of a new user")
    @ResponseStatus(HttpStatus.CREATED)
    @Audit(
            type = AuditType.CREATE,
            entityType = "USER",
            entityIdParam = "result.body.id"

    )
    @TriggerAlertOnFailure(
            triggerOn = {UserNotFoundException.class, IllegalArgumentException.class },
            threshold = 3,
            auditType = AuditType.MEDIUM
    )
    @Operation(summary = "Register a new user (username/email/password)")
    @ApiResponse(
            responseCode = "201",
            description = "User successfully registered",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    @ApiResponse(responseCode = "400", description = "Invalid input data")
    @ApiResponse(responseCode = "409", description = "Username or email already exists")//errorResponse.class
    public ResponseEntity<UserResponse> create(@PathVariable("spaceId") UUID spaceId,
                                               @Valid @RequestBody  CreateUserRequestV2 req) {
        // Log technique: payload masqué par CreateUserRequestV2.toString() + MaskingLogger
        log.info("Create user request received for spaceId={} payload={}", spaceId, req);

        // Log plus détaillé pour le debug
        log.debug("CreateUserRequestV2 (debug) for spaceId={}: {}", spaceId, req);

        CreateUserCommand cmd = CreateUserCommand.from(spaceId, req);

        // Log sur la commande (normalement sans secret, mais possible d'ajouter un toString masqué si besoin)
        log.debug("CreateUserCommand built for spaceId={}: {}", spaceId, cmd);

        UserResponse created = service.createUser(cmd);

        // Log de sortie: pas de mot de passe ici, uniquement identifiants fonctionnels
        log.info("User successfully created: userId={}, spaceId={}, accountId={}",
                created.id(), created.spaceId(), created.id());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}