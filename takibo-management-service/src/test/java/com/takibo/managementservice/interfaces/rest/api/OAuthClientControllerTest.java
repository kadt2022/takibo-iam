package com.takibo.managementservice.interfaces.rest.api;

import com.takibo.managementservice.application.command.RegisterClientCommand;
import com.takibo.managementservice.application.mapper.ClientRegistrationMapper;
import com.takibo.managementservice.application.service.OAuthClientService;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.RegisteredClientResult;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.interfaces.rest.response.ClientRegistrationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OAuthClientController(service, mapper)).build();
    }

    @Test
    void register_confidential_exposes_expiration_only_inside_client() throws Exception {
        Instant expiresAt = Instant.parse("2026-03-20T00:00:00Z");
        OAuthClient domainClient = OAuthClient.create(ORG_ID, SpaceId.of(SPACE_ID), "conf-client", "Conf Client", ClientType.CONFIDENTIAL);

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

        mockMvc.perform(post("/api/orgs/{orgId}/spaces/{spaceId}/clients", ORG_ID, SPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "conf-client",
                                  "clientName": "Conf Client",
                                  "clientType": "CONFIDENTIAL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oneTimePlainSecret").value("one-time-secret"))
                .andExpect(jsonPath("$.client.clientSecretExpiresAt").exists())
                .andExpect(jsonPath("$.clientSecretExpiresAt").doesNotExist());
    }

    @Test
    void register_public_returns_null_secret_and_no_top_level_expiration() throws Exception {
        OAuthClient domainClient = OAuthClient.create(ORG_ID, SpaceId.of(SPACE_ID), "pub-client", "Pub Client", ClientType.PUBLIC);

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

        mockMvc.perform(post("/api/orgs/{orgId}/spaces/{spaceId}/clients", ORG_ID, SPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "pub-client",
                                  "clientName": "Pub Client",
                                  "clientType": "PUBLIC"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oneTimePlainSecret").isEmpty())
                .andExpect(jsonPath("$.clientSecretExpiresAt").doesNotExist());
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
