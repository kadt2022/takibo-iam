package com.takibo.managementservice.interfaces.rest.api;


import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.port.CurrentActorProvider;
import com.takibo.managementservice.application.query.port.SpaceQueryCase;
import com.takibo.managementservice.application.security.ActorSource;
import com.takibo.managementservice.application.service.SpaceApplicationService;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.interfaces.rest.mapper.SpaceRestMapper;
import com.takibo.managementservice.interfaces.rest.request.CreateSpaceRequest;
import com.takibo.managementservice.interfaces.rest.response.SpacePageResponse;
import com.takibo.managementservice.interfaces.rest.response.SpaceResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Surface spaces du plan de management (TMS) : identification par UUID, machine-first —
 * les codes lisibles restent le langage du plan identité (TIS-CORE).
 * Toute la surface est gardée par le PolicyEvaluator : autorité ORG requise
 * (R_ORG_OWNER/R_ORG_ADMIN), lecture du détail aussi ouverte au R_SPACE_ADMIN de ce space.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/spaces")
@RequiredArgsConstructor
@Validated
@Slf4j
public class SpaceController {

    private final SpaceApplicationService service;
    private final SpaceQueryCase spaceQueryCase;
    private final SpaceRestMapper restMapper;
    private final CurrentActorProvider actorProvider;

    @PostMapping
    public ResponseEntity<SpaceResponse> createSpace(@PathVariable("orgId") UUID orgId,
                                                     @Valid @RequestBody CreateSpaceRequest req) {

        // IAM 31 : le propriétaire d'un space est un ACCOUNT (le user local est une
        // réalité de space) — l'ancien passage du userId violait fk_spaces_owner_account_scope.
        UUID ownerAccountId = actorProvider.currentAccountId();
        ActorSource source = actorProvider.source();

        log.info("Create space request orgId={} ownerAccountId={} source={} payload={}",
            orgId, ownerAccountId, source, req);

        CreateSpaceCommand cmd = CreateSpaceCommand.from(orgId, ownerAccountId, source, req);

        SpaceResponse created = service.createSpace(cmd);

        log.info("Space created spaceId={} orgId={} code={} createdByUserId={}",
            created.id(), created.orgId(), created.code(), created.ownerAccountId());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{spaceId}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public ResponseEntity<SpacePageResponse> listSpaces(
            @PathVariable("orgId") UUID orgId,
            @RequestParam(value = "status", required = false) SpaceStatus status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", required = false) String sort) {

        return ResponseEntity.ok(restMapper.toPageResponse(
                spaceQueryCase.listSpaces(orgId, status, search, page, size, sort)));
    }

    @GetMapping("/{spaceId}")
    public ResponseEntity<SpaceResponse> getSpace(@PathVariable("orgId") UUID orgId,
                                                  @PathVariable("spaceId") UUID spaceId) {

        return ResponseEntity.ok(restMapper.toSpaceResponse(spaceQueryCase.getSpace(orgId, spaceId)));
    }
}
