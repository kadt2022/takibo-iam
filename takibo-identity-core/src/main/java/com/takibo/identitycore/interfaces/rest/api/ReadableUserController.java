package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.audit.annotations.Audit;
import com.takibo.audit.annotations.LogAction;
import com.takibo.audit.annotations.TriggerAlertOnFailure;
import com.takibo.audit.domain.AuditType;
import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.application.identity.port.UserApplicationCase;
import com.takibo.identitycore.domain.exception.OrganizationNotFoundException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.request.CreateUserRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Façade lisible de création d'utilisateur.
 * <p>
 * Reçoit les codes humains {@code orgCode}/{@code spaceCode}, les résout en
 * {@code spaceId} via le port externe {@link SpaceKeyResolutionCase} (implémenté par le TMS),
 * puis délègue à la MÊME logique de création que la route par UUID ({@link UserApplicationCase}).
 * <p>
 * Aucune logique métier n'est dupliquée et aucune entité de persistance n'est manipulée ici.
 * <p>
 * Pas de {@code @RequireActiveSpace} : l'aspect s'appuie sur un path {@code {spaceId}} absent ici.
 * La garde « space actif » est déjà appliquée en aval par le flux de création
 * (UserRegistrationOrchestrator -> SpaceContextVerifier).
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgCode}/spaces/{spaceCode}/users")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ReadableUserController {

    private final SpaceKeyResolutionCase spaceKeyResolution;
    private final UserApplicationCase service;

    @PostMapping
    @LogAction("Registration of a new user (readable route)")
    @ResponseStatus(HttpStatus.CREATED)
    @Audit(
            type = AuditType.CREATE,
            entityType = "USER",
            entityIdParam = "result.body.id"
    )
    @TriggerAlertOnFailure(
            triggerOn = {OrganizationNotFoundException.class, SpaceNotFoundException.class, IllegalArgumentException.class},
            threshold = 3,
            auditType = AuditType.MEDIUM
    )
    @Operation(summary = "Register a new user via human-readable org/space codes")
    @ApiResponse(
            responseCode = "201",
            description = "User successfully registered",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    @ApiResponse(responseCode = "400", description = "Invalid input data")
    @ApiResponse(responseCode = "404", description = "Organization or space not found")
    @ApiResponse(responseCode = "409", description = "Username or email already exists")
    public ResponseEntity<UserResponse> create(@PathVariable("orgCode") String orgCode,
                                               @PathVariable("spaceCode") String spaceCode,
                                               @Valid @RequestBody CreateUserRequest req) {

        ResolvedSpaceKey key = spaceKeyResolution.resolve(orgCode, spaceCode);

        log.info("Create user via readable route orgCode={} spaceCode={} -> orgId={} spaceId={}",
                key.orgCode(), key.spaceCode(), key.orgId(), key.spaceId());

        CreateUserCommand cmd = CreateUserCommand.from(key.spaceId(), req);
        UserResponse created = service.createUser(cmd);

        log.info("User successfully created via readable route: userId={}, spaceId={}",
                created.id(), key.spaceId());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
