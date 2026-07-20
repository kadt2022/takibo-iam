package com.takibo.managementservice.interfaces.rest.api;

import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
import com.takibo.identitycore.integration.security.port.CurrentSpaceContextCase;
import com.takibo.identitycore.integration.space.annotations.RequireActiveSpace;
import com.takibo.identitycore.integration.space.port.SpaceOwnershipGuardCase;
import com.takibo.managementservice.application.command.RegisterClientCommand;
import com.takibo.managementservice.application.mapper.ClientRegistrationMapper;
import com.takibo.managementservice.application.service.OAuthClientService;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.RegisteredClientResult;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.interfaces.rest.response.ClientRegistrationResponse;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OAuthClientControllerTest {

    private static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SPACE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private OAuthClientService service;

    @Mock
    private ClientRegistrationMapper mapper;

    @Mock
    private CurrentOrganizationContextCase currentOrganizationContext;

    @Mock
    private CurrentSpaceContextCase currentSpaceContext;

    @Mock
    private SpaceOwnershipGuardCase spaceOwnershipGuard;

    private OAuthClientController controller;
    private MockMvc mockMvc;
    private ValidatorFactory validatorFactory;

    @BeforeEach
    void setUp() {
        // Couture réelle : la frontière token<->chemin est portée par SpaceBoundaryGuard
        // (org ET space du token), pas par une garde locale au contrôleur.
        SpaceBoundaryGuard spaceBoundaryGuard =
                new SpaceBoundaryGuard(currentOrganizationContext, currentSpaceContext);
        controller = new OAuthClientController(service, mapper, spaceBoundaryGuard, spaceOwnershipGuard);
        validatorFactory = Validation.buildDefaultValidatorFactory();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new SpringValidatorAdapter(validatorFactory.getValidator()))
                .build();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void controller_is_mounted_on_v1_route() {
        String[] paths = OAuthClientController.class
                .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class)
                .value();

        assertThat(paths).containsExactly("/api/v1/orgs/{orgId}/spaces/{spaceId}/clients");
    }

    @Test
    void controller_still_requires_active_space() {
        // Non-régression : la création dans un space suspendu reste refusée par
        // @RequireActiveSpace (SpaceActiveAspect) — la garde ne doit pas disparaître.
        assertThat(OAuthClientController.class.getAnnotation(RequireActiveSpace.class)).isNotNull();
    }

    @Test
    void register_rejects_unsafe_client_identifier_before_calling_the_service() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/spaces/{spaceId}/clients", ORG_ID, SPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "unsafe client id",
                                  "clientName": "Unsafe Client",
                                  "clientType": "CONFIDENTIAL",
                                  "grantTypes": ["client_credentials"]
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rotateSecret_rejects_expired_expiration_before_calling_the_service() throws Exception {
        UUID clientId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        mockMvc.perform(post(
                        "/api/v1/orgs/{orgId}/spaces/{spaceId}/clients/{id}/rotate-secret",
                        ORG_ID,
                        SPACE_ID,
                        clientId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientSecretExpiresAt\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void register_confidential_exposes_expiration_only_inside_client() throws Exception {
        Instant expiresAt = Instant.parse("2026-03-20T00:00:00Z");
        OAuthClient domainClient = OAuthClient.create(ORG_ID, SpaceId.of(SPACE_ID), "conf-client", "Conf Client", ClientType.CONFIDENTIAL);

        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_ID);
        when(currentSpaceContext.requireCurrentSpaceId()).thenReturn(SPACE_ID);
        when(mapper.toCommand(any())).thenReturn(commandFor(ClientType.CONFIDENTIAL));
        when(service.register(eq(ORG_ID), eq(SpaceId.of(SPACE_ID)), any(RegisterClientCommand.class)))
                .thenReturn(new RegisteredClientResult(domainClient, "one-time-secret"));
        when(mapper.toResponse(domainClient)).thenReturn(new ClientRegistrationResponse(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                ORG_ID,
                SPACE_ID,
                "conf-client",
                "Conf Client",
                ClientType.CONFIDENTIAL,
                true,
                false,
                expiresAt,
                Set.of("api:read"),
                Set.of("client_credentials"),
                Set.of(),
                Set.of(),
                Set.of()
        ));

        mockMvc.perform(post("/api/v1/orgs/{orgId}/spaces/{spaceId}/clients", ORG_ID, SPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "conf-client",
                                  "clientName": "Conf Client",
                                  "clientType": "CONFIDENTIAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/api/v1/orgs/%s/spaces/%s/clients/33333333-3333-3333-3333-333333333333"
                                .formatted(ORG_ID, SPACE_ID)))
                .andExpect(header().string("Cache-Control", "no-store, no-cache"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.oneTimePlainSecret").value("one-time-secret"))
                .andExpect(jsonPath("$.client.clientSecretExpiresAt").exists())
                .andExpect(jsonPath("$.clientSecretExpiresAt").doesNotExist());

        verify(spaceOwnershipGuard).assertSpaceBelongsToOrg(SPACE_ID, ORG_ID);
    }

    @Test
    void register_public_returns_null_secret_and_no_top_level_expiration() throws Exception {
        OAuthClient domainClient = OAuthClient.create(ORG_ID, SpaceId.of(SPACE_ID), "pub-client", "Pub Client", ClientType.PUBLIC);

        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_ID);
        when(currentSpaceContext.requireCurrentSpaceId()).thenReturn(SPACE_ID);
        when(mapper.toCommand(any())).thenReturn(commandFor(ClientType.PUBLIC));
        when(service.register(eq(ORG_ID), eq(SpaceId.of(SPACE_ID)), any(RegisterClientCommand.class)))
                .thenReturn(new RegisteredClientResult(domainClient, null));
        when(mapper.toResponse(domainClient)).thenReturn(new ClientRegistrationResponse(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                ORG_ID,
                SPACE_ID,
                "pub-client",
                "Pub Client",
                ClientType.PUBLIC,
                false,
                true,
                null,
                Set.of("api:read"),
                Set.of("authorization_code"),
                Set.of("https://app.example/callback"),
                Set.of(),
                Set.of("https://app.example")
        ));

        mockMvc.perform(post("/api/v1/orgs/{orgId}/spaces/{spaceId}/clients", ORG_ID, SPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "pub-client",
                                  "clientName": "Pub Client",
                                  "clientType": "PUBLIC"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/api/v1/orgs/%s/spaces/%s/clients/44444444-4444-4444-4444-444444444444"
                                .formatted(ORG_ID, SPACE_ID)))
                .andExpect(jsonPath("$.oneTimePlainSecret").isEmpty())
                .andExpect(jsonPath("$.clientSecretExpiresAt").doesNotExist());
    }

    @Test
    void rotateSecret_keeps_ok_status_and_secret_protection_headers() throws Exception {
        UUID clientId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        OAuthClient domainClient = OAuthClient.create(
                ORG_ID,
                SpaceId.of(SPACE_ID),
                "conf-client",
                "Conf Client",
                ClientType.CONFIDENTIAL
        );

        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_ID);
        when(currentSpaceContext.requireCurrentSpaceId()).thenReturn(SPACE_ID);
        when(service.rotateSecret(ORG_ID, SpaceId.of(SPACE_ID), clientId, null))
                .thenReturn(new RegisteredClientResult(domainClient, "rotated-secret"));

        mockMvc.perform(post(
                        "/api/v1/orgs/{orgId}/spaces/{spaceId}/clients/{id}/rotate-secret",
                        ORG_ID,
                        SPACE_ID,
                        clientId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store, no-cache"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.clientId").value("conf-client"))
                .andExpect(jsonPath("$.oneTimePlainSecret").value("rotated-secret"));

        verify(spaceOwnershipGuard).assertSpaceBelongsToOrg(SPACE_ID, ORG_ID);
    }

    @Test
    void register_without_org_context_is_denied_and_does_not_register() {
        when(currentOrganizationContext.requireCurrentOrganizationId())
                .thenThrow(new AccessDeniedException("ORG_CONTEXT_REQUIRED"));

        assertThatThrownBy(() -> controller.register(ORG_ID, SPACE_ID, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ORG_CONTEXT_REQUIRED");

        verifyNoInteractions(service);
    }

    @Test
    void register_for_another_org_is_denied_and_does_not_register() {
        UUID otherOrg = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(otherOrg);

        assertThatThrownBy(() -> controller.register(ORG_ID, SPACE_ID, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ORG_MISMATCH");

        verifyNoInteractions(service);
    }

    @Test
    void register_with_token_of_another_space_is_denied_and_does_not_register() {
        // La faille IAM 33 : token.org == path.org ne suffit PAS — le token doit
        // désigner exactement le space du chemin, aucun secret ne sort après refus.
        UUID otherSpace = UUID.fromString("88888888-8888-8888-8888-888888888888");
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_ID);
        when(currentSpaceContext.requireCurrentSpaceId()).thenReturn(otherSpace);

        assertThatThrownBy(() -> controller.register(ORG_ID, SPACE_ID, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("SPACE_CONTEXT_MISMATCH");

        verifyNoInteractions(service);
    }

    @Test
    void rotateSecret_with_token_of_another_space_is_denied_and_returns_no_secret() {
        UUID otherSpace = UUID.fromString("88888888-8888-8888-8888-888888888888");
        UUID clientId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_ID);
        when(currentSpaceContext.requireCurrentSpaceId()).thenReturn(otherSpace);

        assertThatThrownBy(() -> controller.rotateSecret(ORG_ID, SPACE_ID, clientId, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("SPACE_CONTEXT_MISMATCH");

        verify(service, never()).rotateSecret(any(), any(), any(), any());
        verifyNoInteractions(service);
    }

    @Test
    void rotateSecret_without_space_context_is_denied() {
        // Token ORGANIZATION (sans space_id) : la surface reste inutilisable
        // jusqu'à l'échange ORG->SPACE (IAM 34).
        UUID clientId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_ID);
        when(currentSpaceContext.requireCurrentSpaceId())
                .thenThrow(new AccessDeniedException("SPACE_CONTEXT_REQUIRED"));

        assertThatThrownBy(() -> controller.rotateSecret(ORG_ID, SPACE_ID, clientId, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("SPACE_CONTEXT_REQUIRED");

        verifyNoInteractions(service);
    }

    private static RegisterClientCommand commandFor(ClientType type) {
        return new RegisterClientCommand(
                "dummy-client",
                "Dummy Client",
                type,
                type == ClientType.CONFIDENTIAL,
                type == ClientType.CONFIDENTIAL ? com.takibo.managementservice.domain.model.TokenEndpointAuthMethod.client_secret_basic : com.takibo.managementservice.domain.model.TokenEndpointAuthMethod.none,
                type == ClientType.PUBLIC,
                false,
                null,
                null,
                "RS256",
                900,
                3600,
                900,
                null,
                Set.of("api:read"),
                type == ClientType.CONFIDENTIAL ? Set.of("client_credentials") : Set.of("authorization_code"),
                type == ClientType.PUBLIC ? Set.of("https://app.example/callback") : Set.of(),
                Set.of(),
                type == ClientType.PUBLIC ? Set.of("https://app.example") : Set.of()
        );
    }
}
