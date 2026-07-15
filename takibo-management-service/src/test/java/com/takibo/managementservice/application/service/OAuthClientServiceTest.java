package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.RegisterClientCommand;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.exception.OAuthClientSecretRotationConflictException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.RegisteredClientResult;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.repository.OAuthClientRepository;
import com.takibo.managementservice.domain.vo.OAuthClientId;
import com.takibo.managementservice.domain.vo.SpaceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthClientServiceTest {

    @Mock
    private OAuthClientRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OAuthClientService service;

    @Captor
    private ArgumentCaptor<OAuthClient> clientCaptor;

    @Test
    void register_normalizes_clientCredentials_profile() {
        RegisterClientCommand cmd = baseCommand()
                .withClientType(ClientType.PUBLIC)
                .withTokenEndpointAuthMethod(TokenEndpointAuthMethod.none)
                .withRequireClientSecret(false)
                .withRequirePkce(true)
                .withRequireConsent(true)
                .withGrantTypes(Set.of("client_credentials"))
                .withRedirectUris(Set.of())
                .withPostLogoutRedirectUris(Set.of())
                .withCorsOrigins(Set.of())
                .build();

        when(repository.existsByClientId(cmd.clientId())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisteredClientResult result = service.register(UUID.randomUUID(), SpaceId.of(UUID.randomUUID()), cmd);

        verify(repository).save(clientCaptor.capture());
        OAuthClient saved = clientCaptor.getValue();
        assertThat(saved.getClientType()).isEqualTo(ClientType.CONFIDENTIAL);
        assertThat(saved.getTokenEndpointAuthMethod()).isEqualTo(TokenEndpointAuthMethod.client_secret_basic);
        assertThat(saved.isRequirePkce()).isFalse();
        assertThat(saved.isRequireConsent()).isFalse();
        assertThat(saved.getRedirectUris()).isEmpty();
        assertThat(saved.getPostLogoutRedirectUris()).isEmpty();
        assertThat(saved.getCorsOrigins()).isEmpty();
        assertThat(result.oneTimePlainSecret()).isNotBlank();
    }

    @Test
    void register_rejects_clientCredentials_mixed_with_other_grants() {
        RegisterClientCommand cmd = baseCommand()
                .withClientType(ClientType.PUBLIC)
                .withGrantTypes(Set.of("client_credentials", "authorization_code"))
                .withRedirectUris(Set.of("http://localhost/callback"))
                .build();

        when(repository.existsByClientId(cmd.clientId())).thenReturn(false);

        assertThatThrownBy(() -> service.register(UUID.randomUUID(), SpaceId.of(UUID.randomUUID()), cmd))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessageContaining("client_credentials cannot be combined");
    }

    @Test
    void register_rejects_clientCredentials_with_redirects() {
        RegisterClientCommand cmd = baseCommand()
                .withClientType(ClientType.CONFIDENTIAL)
                .withGrantTypes(Set.of("client_credentials"))
                .withRedirectUris(Set.of("http://localhost/callback"))
                .build();

        when(repository.existsByClientId(cmd.clientId())).thenReturn(false);

        assertThatThrownBy(() -> service.register(UUID.randomUUID(), SpaceId.of(UUID.randomUUID()), cmd))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessageContaining("redirect/cors/post-logout");
    }

    @Test
    void rotateSecret_unknownClient_and_clientOfAnotherSpace_yieldIdenticalResponse() {
        UUID orgId = UUID.randomUUID();
        SpaceId spaceId = SpaceId.of(UUID.randomUUID());

        UUID unknownClientId = UUID.randomUUID();
        when(repository.findByIdAndOrgIdAndSpaceId(unknownClientId, orgId, spaceId.value()))
                .thenReturn(Optional.empty());

        Throwable unknown = catchThrowable(() ->
                service.rotateSecret(orgId, spaceId, unknownClientId, null));

        UUID foreignClientId = UUID.randomUUID();
        when(repository.findByIdAndOrgIdAndSpaceId(foreignClientId, orgId, spaceId.value()))
                .thenReturn(Optional.empty());

        Throwable foreign = catchThrowable(() ->
                service.rotateSecret(orgId, spaceId, foreignClientId, null));

        assertThat(unknown).isInstanceOf(InvalidClientConfigurationException.class);
        assertThat(foreign).isInstanceOf(InvalidClientConfigurationException.class);
        assertThat(foreign.getClass()).isEqualTo(unknown.getClass());
        assertThat(foreign.getMessage()).isEqualTo(unknown.getMessage());

        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void rotateSecret_updatesSecretWithSituatedPersistence_withoutSavingWholeAggregate() {
        UUID orgId = UUID.randomUUID();
        SpaceId spaceId = SpaceId.of(UUID.randomUUID());
        UUID clientId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2035-01-01T00:00:00Z");
        OAuthClient existing = OAuthClient.builder()
                .id(OAuthClientId.of(clientId))
                .orgId(orgId)
                .spaceId(spaceId)
                .clientId("machine-client")
                .clientName("Machine Client")
                .clientType(ClientType.CONFIDENTIAL)
                .tokenEndpointAuthMethod(TokenEndpointAuthMethod.client_secret_basic)
                .version(7L)
                .scopes(Set.of("api:read"))
                .grantTypes(Set.of("client_credentials"))
                .build();

        when(repository.findByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId.value()))
                .thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(any())).thenReturn("encoded-secret");
        when(repository.updateSecretByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId.value(),
                7L, "encoded-secret", expiresAt)).thenReturn(true);

        RegisteredClientResult result = service.rotateSecret(orgId, spaceId, clientId, expiresAt);

        assertThat(result.client().getClientSecretHash()).isEqualTo("encoded-secret");
        assertThat(result.client().getClientSecretExpiresAt()).isEqualTo(expiresAt);
        assertThat(result.client().getScopes()).containsExactly("api:read");
        assertThat(result.client().getGrantTypes()).containsExactly("client_credentials");
        assertThat(result.oneTimePlainSecret()).isNotBlank();
        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void rotateSecret_concurrentVersionConflict_doesNotReturnPlainSecret() {
        UUID orgId = UUID.randomUUID();
        SpaceId spaceId = SpaceId.of(UUID.randomUUID());
        UUID clientId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2035-01-01T00:00:00Z");
        OAuthClient existing = OAuthClient.builder()
                .id(OAuthClientId.of(clientId))
                .orgId(orgId)
                .spaceId(spaceId)
                .clientId("machine-client")
                .clientName("Machine Client")
                .clientType(ClientType.CONFIDENTIAL)
                .tokenEndpointAuthMethod(TokenEndpointAuthMethod.client_secret_basic)
                .version(4L)
                .build();

        when(repository.findByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId.value()))
                .thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(any())).thenReturn("encoded-secret");
        when(repository.updateSecretByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId.value(),
                4L, "encoded-secret", expiresAt)).thenReturn(false);

        Throwable failure = catchThrowable(() -> service.rotateSecret(orgId, spaceId, clientId, expiresAt));

        assertThat(failure).isInstanceOf(OAuthClientSecretRotationConflictException.class);
        verify(repository, never()).save(any());
    }

    private static RegisterClientCommandBuilder baseCommand() {
        return new RegisterClientCommandBuilder();
    }

    private static final class RegisterClientCommandBuilder {
        private String clientId = "client-test";
        private String clientName = "Client Test";
        private ClientType clientType = ClientType.PUBLIC;
        private Boolean requireClientSecret = false;
        private TokenEndpointAuthMethod tokenEndpointAuthMethod = TokenEndpointAuthMethod.none;
        private Boolean requirePkce = true;
        private Boolean requireConsent = false;
        private Set<String> scopes = Set.of("api:read");
        private Set<String> grantTypes = Set.of("authorization_code");
        private Set<String> redirectUris = Set.of("http://localhost/callback");
        private Set<String> postLogoutRedirectUris = Set.of("http://localhost/logout");
        private Set<String> corsOrigins = Set.of("http://localhost");

        RegisterClientCommandBuilder withClientType(ClientType value) {
            this.clientType = value;
            return this;
        }

        RegisterClientCommandBuilder withRequireClientSecret(Boolean value) {
            this.requireClientSecret = value;
            return this;
        }

        RegisterClientCommandBuilder withTokenEndpointAuthMethod(TokenEndpointAuthMethod value) {
            this.tokenEndpointAuthMethod = value;
            return this;
        }

        RegisterClientCommandBuilder withRequirePkce(Boolean value) {
            this.requirePkce = value;
            return this;
        }

        RegisterClientCommandBuilder withRequireConsent(Boolean value) {
            this.requireConsent = value;
            return this;
        }

        RegisterClientCommandBuilder withGrantTypes(Set<String> value) {
            this.grantTypes = value;
            return this;
        }

        RegisterClientCommandBuilder withRedirectUris(Set<String> value) {
            this.redirectUris = value;
            return this;
        }

        RegisterClientCommandBuilder withPostLogoutRedirectUris(Set<String> value) {
            this.postLogoutRedirectUris = value;
            return this;
        }

        RegisterClientCommandBuilder withCorsOrigins(Set<String> value) {
            this.corsOrigins = value;
            return this;
        }

        RegisterClientCommand build() {
            return new RegisterClientCommand(
                    clientId,
                    clientName,
                    clientType,
                    requireClientSecret,
                    tokenEndpointAuthMethod,
                    requirePkce,
                    requireConsent,
                    null,
                    null,
                    "RS256",
                    900,
                    3600,
                    900,
                    null,
                    scopes,
                    grantTypes,
                    redirectUris,
                    postLogoutRedirectUris,
                    corsOrigins
            );
        }
    }
}
