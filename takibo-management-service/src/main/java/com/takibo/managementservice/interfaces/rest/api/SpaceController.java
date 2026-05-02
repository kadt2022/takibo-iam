package com.takibo.managementservice.interfaces.rest.api;


import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.port.CurrentActorProvider;
import com.takibo.managementservice.application.security.ActorSource;
import com.takibo.managementservice.application.service.SpaceApplicationService;
import com.takibo.managementservice.domain.exception.OrganizationDisabledException;
import com.takibo.managementservice.domain.exception.SpaceQuotaExceededException;
import com.takibo.managementservice.interfaces.rest.request.CreateSpaceRequest;
import com.takibo.managementservice.interfaces.rest.response.SpaceResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/spaces")
@RequiredArgsConstructor
@Validated
@Slf4j
public class SpaceController {

    private final SpaceApplicationService service;
    private final CurrentActorProvider actorProvider;

    @PostMapping
//    @PreAuthorize("hasAuthority('P_CREATE_SPACE')")
//    @LogAction("Creation of a new port")
//    @Audit(type = AuditType.CREATE, entityType = "SPACE", entityIdParam = "result.body.id")
//    @TriggerAlertOnFailure(
//        triggerOn = {OrganizationDisabledException.class, SpaceQuotaExceededException.class},
//        threshold = 3,
//        auditType = AuditType.MEDIUM
//    )
    public ResponseEntity<SpaceResponse> createSpace(@PathVariable("orgId") UUID orgId,
                                                     @Valid @RequestBody CreateSpaceRequest req) {

        UUID creatorUserId = actorProvider.currentUserId();
        ActorSource source = actorProvider.source();

        log.info("Create port request orgId={} actorUserId={} source={} payload={}",
            orgId, creatorUserId, source, req);

        CreateSpaceCommand cmd = CreateSpaceCommand.from(orgId, creatorUserId, source, req);

        SpaceResponse created = service.createSpace(cmd);

        log.info("Space created spaceId={} orgId={} code={} createdByUserId={}",
            created.id(), created.orgId(), created.code(), created.ownerAccountId());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
