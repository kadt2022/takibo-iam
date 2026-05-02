package com.takibo.managementservice.interfaces.rest.api;

import com.takibo.identitycore.integration.space.annotations.RequireActiveSpace;
import com.takibo.managementservice.application.mapper.ClientRegistrationMapper;
import com.takibo.managementservice.application.service.OAuthClientService;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.interfaces.rest.request.ClientRegistrationRequest;
import com.takibo.managementservice.interfaces.rest.request.RotateClientSecretRequest;
import com.takibo.managementservice.interfaces.rest.response.ClientRegistrationResultResponse;
import com.takibo.managementservice.interfaces.rest.response.ClientSecretResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequireActiveSpace
@RequestMapping("/api/orgs/{orgId}/spaces/{spaceId}/clients")
public class OAuthClientController {

    private final OAuthClientService service;
    private final ClientRegistrationMapper mapper;

    @PostMapping
    public ResponseEntity<ClientRegistrationResultResponse> register(
            @PathVariable("orgId") UUID orgId,
            @PathVariable("spaceId") UUID spaceId,
            @Valid @RequestBody ClientRegistrationRequest req) {

        var result = service.register(orgId, SpaceId.of(spaceId), mapper.toCommand(req));
        var response = new ClientRegistrationResultResponse(
                mapper.toResponse(result.client()),
                result.oneTimePlainSecret()
        );
        return secretHeaders().body(response);
    }

    @PostMapping("/{id}/rotate-secret")
    public ResponseEntity<ClientSecretResponse> rotateSecret(
            @PathVariable("orgId") UUID orgId,
            @PathVariable("spaceId") UUID spaceId,
            @PathVariable("id") UUID clientId,
            @Valid @RequestBody RotateClientSecretRequest req) {

        var result = service.rotateSecret(orgId, SpaceId.of(spaceId), clientId, req.clientSecretExpiresAt());
        var response = new ClientSecretResponse(
                result.client().getClientId(),
                result.oneTimePlainSecret(),
                result.client().getClientSecretExpiresAt()
        );
        return secretHeaders().body(response);
    }

    private static ResponseEntity.BodyBuilder secretHeaders() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache")
                .header("Pragma", "no-cache")
                .header("X-Content-Type-Options", "nosniff");
    }
}
