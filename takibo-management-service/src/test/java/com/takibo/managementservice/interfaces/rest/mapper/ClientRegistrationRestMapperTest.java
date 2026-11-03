package com.takibo.managementservice.interfaces.rest.mapper;

import com.takibo.managementservice.application.command.RegisterClientCommand;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.vo.OAuthClientId;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.interfaces.rest.request.ClientRegistrationRequest;
import com.takibo.managementservice.interfaces.rest.response.ClientRegistrationResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRegistrationRestMapperTest {

    private static final Instant EXPIRY = Instant.parse("2031-01-01T00:00:00Z");

    private final ClientRegistrationRestMapper mapper =
            new ClientRegistrationRestMapperImpl();

    @Test
    void toCommand_transposesEveryRegistrationField() {
        ClientRegistrationRequest request = new ClientRegistrationRequest(
                "partner-api",
                "Partner API",
                ClientType.CONFIDENTIAL,
                false,
                TokenEndpointAuthMethod.private_key_jwt,
                true,
                false,
                "https://keys.example/jwks",
                "{\"keys\":[]}",
                "PS256",
                600,
                7200,
                300,
                EXPIRY,
                Set.of("api:read", "api:write"),
                Set.of("authorization_code", "refresh_token"),
                Set.of("https://partner.example/callback"),
                Set.of("https://partner.example/bye"),
                Set.of("https://partner.example")
        );

        RegisterClientCommand command = mapper.toCommand(request);

        assertThat(command).isEqualTo(new RegisterClientCommand(
                "partner-api",
                "Partner API",
                ClientType.CONFIDENTIAL,
                false,
                TokenEndpointAuthMethod.private_key_jwt,
                true,
                false,
                "https://keys.example/jwks",
                "{\"keys\":[]}",
                "PS256",
                600,
                7200,
                300,
                EXPIRY,
                Set.of("api:read", "api:write"),
                Set.of("authorization_code", "refresh_token"),
                Set.of("https://partner.example/callback"),
                Set.of("https://partner.example/bye"),
                Set.of("https://partner.example")
        ));
    }

    @Test
    void toCommand_keepsOptionalFieldsUnsetSoDomainDefaultsApply() {
        ClientRegistrationRequest request = new ClientRegistrationRequest(
                "spa", "SPA", ClientType.PUBLIC,
                null, null, null, null, null, null, null, null, null, null, null,
                null, Set.of("authorization_code"), null, null, null
        );

        RegisterClientCommand command = mapper.toCommand(request);

        assertThat(command.requireClientSecret()).isNull();
        assertThat(command.tokenEndpointAuthMethod()).isNull();
        assertThat(command.requirePkce()).isNull();
        assertThat(command.requireConsent()).isNull();
        assertThat(command.scopes()).isNull();
        assertThat(command.redirectUris()).isNull();
        assertThat(command.postLogoutRedirectUris()).isNull();
        assertThat(command.corsOrigins()).isNull();
        assertThat(command.grantTypes()).containsExactly("authorization_code");
    }

    @Test
    void toCommand_copiesCollectionsInsteadOfSharingTheRequestInstances() {
        Set<String> scopes = new HashSet<>(Set.of("api:read"));
        ClientRegistrationRequest request = new ClientRegistrationRequest(
                "spa", "SPA", ClientType.PUBLIC,
                null, null, null, null, null, null, null, null, null, null, null,
                scopes, Set.of("authorization_code"), null, null, null
        );

        RegisterClientCommand command = mapper.toCommand(request);
        scopes.add("api:admin");

        assertThat(command.scopes()).containsExactly("api:read");
    }

    @Test
    void toResponse_exposesTheClientBoundaryAndConfiguration() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        OAuthClient client = OAuthClient.builder()
                .id(OAuthClientId.of(id))
                .orgId(orgId)
                .spaceId(SpaceId.of(spaceId))
                .clientId("partner-api")
                .clientName("Partner API")
                .clientType(ClientType.CONFIDENTIAL)
                .requireClientSecret(true)
                .clientSecretHash("argon2id-hash")
                .clientSecretExpiresAt(EXPIRY)
                .tokenEndpointAuthMethod(TokenEndpointAuthMethod.client_secret_basic)
                .requirePkce(true)
                .scopes(Set.of("api:read"))
                .grantTypes(Set.of("authorization_code"))
                .redirectUris(Set.of("https://partner.example/callback"))
                .postLogoutRedirectUris(Set.of("https://partner.example/bye"))
                .corsOrigins(Set.of("https://partner.example"))
                .build();

        ClientRegistrationResponse response = mapper.toResponse(client);

        assertThat(response).isEqualTo(new ClientRegistrationResponse(
                id,
                orgId,
                spaceId,
                "partner-api",
                "Partner API",
                ClientType.CONFIDENTIAL,
                true,
                true,
                EXPIRY,
                Set.of("api:read"),
                Set.of("authorization_code"),
                Set.of("https://partner.example/callback"),
                Set.of("https://partner.example/bye"),
                Set.of("https://partner.example")
        ));
    }

    @Test
    void toResponse_ofAnOrganizationClientHasNoSpace() {
        OAuthClient client = OAuthClient.create(
                UUID.randomUUID(), null, "org-client", "Org Client", ClientType.CONFIDENTIAL
        );

        ClientRegistrationResponse response = mapper.toResponse(client);

        assertThat(response.orgId()).isEqualTo(client.getOrgId());
        assertThat(response.spaceId()).isNull();
        assertThat(response.id()).isEqualTo(client.getId().getValue());
    }

    @Test
    void toResponse_ofAPlatformClientHasNeitherOrganizationNorSpace() {
        OAuthClient client = OAuthClient.builder()
                .clientId("platform").clientName("Platform")
                .clientType(ClientType.CONFIDENTIAL)
                .build();

        ClientRegistrationResponse response = mapper.toResponse(client);

        assertThat(response.id()).isNull();
        assertThat(response.orgId()).isNull();
        assertThat(response.spaceId()).isNull();
    }

    @Test
    void theResponseContractCarriesNoSecretNorKeyMaterial() {
        Set<String> components = Arrays.stream(
                        ClientRegistrationResponse.class.getRecordComponents()
                )
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(components).noneMatch(name ->
                name.toLowerCase().contains("hash")
                        || name.toLowerCase().contains("jwks")
                        || name.equalsIgnoreCase("clientSecret"));
    }

    @Test
    void nullInputsMapToNull() {
        assertThat(mapper.toCommand(null)).isNull();
        assertThat(mapper.toResponse(null)).isNull();
    }
}
