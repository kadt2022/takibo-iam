package com.takibo.managementservice.interfaces.rest.api;

import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.space.annotations.RequireActiveSpace;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceOwnershipGuardCase;
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
@RequestMapping("/api/v1/orgs/{orgId}/spaces/{spaceId}/clients")
public class OAuthClientController {

    private final OAuthClientService service;
    private final ClientRegistrationMapper mapper;
    private final SpaceBoundaryGuard spaceBoundaryGuard;
    private final SpaceOwnershipGuardCase spaceOwnershipGuard;

    @PostMapping
    public ResponseEntity<ClientRegistrationResultResponse> register(
            @PathVariable("orgId") UUID orgId,
            @PathVariable("spaceId") UUID spaceId,
            @Valid @RequestBody ClientRegistrationRequest req) {

        assertCallerBoundToSpace(orgId, spaceId);
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

        assertCallerBoundToSpace(orgId, spaceId);
        var result = service.rotateSecret(orgId, SpaceId.of(spaceId), clientId, req.clientSecretExpiresAt());
        var response = new ClientSecretResponse(
                result.client().getClientId(),
                result.oneTimePlainSecret(),
                result.client().getClientSecretExpiresAt()
        );
        return secretHeaders().body(response);
    }

    /**
     * Le management de clients est une opération SITUÉE : la frontière est le token,
     * pas l'appartenance déclarée dans le chemin. {@code token.org_id == path.orgId}
     * (sinon ORG_CONTEXT_REQUIRED / ORG_MISMATCH) ET {@code token.space_id == path.spaceId}
     * (sinon SPACE_CONTEXT_REQUIRED / SPACE_CONTEXT_MISMATCH) — aucun rôle n'élargit
     * cette frontière. La cohérence chemin (le space appartient bien à l'org) reste
     * vérifiée en défense en profondeur.
     */
    private void assertCallerBoundToSpace(UUID orgId, UUID spaceId) {
        spaceBoundaryGuard.assertTokenMatches(new ResolvedSpaceKey(orgId, spaceId, null, null));
        spaceOwnershipGuard.assertSpaceBelongsToOrg(spaceId, orgId);
    }

    private static ResponseEntity.BodyBuilder secretHeaders() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache")
                .header("Pragma", "no-cache")
                .header("X-Content-Type-Options", "nosniff");
    }
}
