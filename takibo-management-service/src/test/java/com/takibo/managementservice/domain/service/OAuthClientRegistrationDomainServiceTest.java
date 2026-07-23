package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.Secrets;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.validation.OAuthClientConfigurationValidator;
import com.takibo.managementservice.domain.vo.SpaceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OAuthClientRegistrationDomainServiceTest {

    @Mock
    private OAuthClientConfigurationValidator configurationValidator;

    private OAuthClientRegistrationDomainService service;

    @BeforeEach
    void setUp() {
        service = new OAuthClientRegistrationDomainService(configurationValidator);
    }

    @Test
    void prepares_the_client_credentials_domain_profile() {
        var plan = service.prepareRegistration(clientCredentialsRegistration());

        assertThat(plan.registration().clientType())
                .isEqualTo(ClientType.CONFIDENTIAL);
        assertThat(plan.authMethod())
                .isEqualTo(TokenEndpointAuthMethod.client_secret_basic);
        assertThat(plan.requirePkce()).isFalse();
        assertThat(plan.requireSecret()).isTrue();
        assertThat(plan.registration().requireConsent()).isFalse();
        assertThat(plan.sets().redirectUris()).isEmpty();
        assertThat(plan.sets().postLogoutRedirectUris()).isEmpty();
        assertThat(plan.sets().corsOrigins()).isEmpty();
        verify(configurationValidator)
                .validateRegistration(plan.registration());
    }

    @Test
    void rejects_mixed_client_credentials_grants_before_preparation() {
        OAuthClientRegistration registration = registration(
                ClientType.CONFIDENTIAL,
                TokenEndpointAuthMethod.client_secret_basic,
                true,
                false,
                Set.of("client_credentials", "authorization_code"),
                Set.of(),
                Set.of()
        );

        assertThatThrownBy(() -> service.prepareRegistration(registration))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage(
                        "client_credentials cannot be combined with other grant types"
                );

        verifyNoInteractions(configurationValidator);
    }

    @Test
    void rejects_redirects_for_client_credentials_before_preparation() {
        OAuthClientRegistration registration = registration(
                ClientType.CONFIDENTIAL,
                TokenEndpointAuthMethod.client_secret_basic,
                true,
                false,
                Set.of("client_credentials"),
                Set.of("https://app.example/callback"),
                Set.of()
        );

        assertThatThrownBy(() -> service.prepareRegistration(registration))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage(
                        "client_credentials must not include "
                                + "redirect/cors/post-logout URIs"
                );

        verifyNoInteractions(configurationValidator);
    }

    @Test
    void registration_plan_builds_the_client_inside_the_organization_boundary() {
        UUID organizationId =
                UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        SpaceId spaceId = SpaceId.of(
                UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002")
        );
        OAuthClientRegistration registration = registration(
                ClientType.PUBLIC,
                TokenEndpointAuthMethod.none,
                false,
                true,
                Set.of("authorization_code"),
                Set.of("https://app.example/callback"),
                Set.of("https://app.example")
        );

        var plan = service.prepareRegistration(registration);
        OAuthClient client = plan.createClient(
                organizationId,
                spaceId,
                Secrets.none()
        );

        assertThat(client.getOrgId()).isEqualTo(organizationId);
        assertThat(client.getSpaceId()).isEqualTo(spaceId);
        assertThat(client.getClientId()).isEqualTo("client-test");
        assertThat(client.getClientName()).isEqualTo("Client Test");
        assertThat(client.getClientType()).isEqualTo(ClientType.PUBLIC);
        assertThat(client.getTokenEndpointAuthMethod())
                .isEqualTo(TokenEndpointAuthMethod.none);
        assertThat(client.isRequirePkce()).isTrue();
        assertThat(client.getRedirectUris())
                .containsExactly("https://app.example/callback");
        assertThat(client.getCorsOrigins())
                .containsExactly("https://app.example");
    }

    @Test
    void secret_rotation_policy_rejects_clients_that_do_not_use_secrets() {
        OAuthClient publicClient = OAuthClient.builder()
                .clientType(ClientType.PUBLIC)
                .tokenEndpointAuthMethod(TokenEndpointAuthMethod.none)
                .build();

        assertThatThrownBy(() ->
                service.resolveSecretRotationExpiration(publicClient, null)
        )
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage("client does not use secrets");
    }

    @Test
    void secret_rotation_policy_keeps_existing_expiration_when_none_is_requested() {
        Instant existingExpiration = Instant.parse("2035-01-01T00:00:00Z");
        OAuthClient confidentialClient = OAuthClient.builder()
                .clientType(ClientType.CONFIDENTIAL)
                .tokenEndpointAuthMethod(
                        TokenEndpointAuthMethod.client_secret_basic
                )
                .clientSecretExpiresAt(existingExpiration)
                .build();

        assertThat(service.resolveSecretRotationExpiration(
                confidentialClient,
                null
        )).isEqualTo(existingExpiration);
        verify(configurationValidator)
                .validateSecretExpiration(existingExpiration);
    }

    private static OAuthClientRegistration clientCredentialsRegistration() {
        return registration(
                ClientType.PUBLIC,
                TokenEndpointAuthMethod.none,
                false,
                true,
                Set.of("client_credentials"),
                Set.of(),
                Set.of()
        );
    }

    private static OAuthClientRegistration registration(
            ClientType clientType,
            TokenEndpointAuthMethod authMethod,
            Boolean requireSecret,
            Boolean requirePkce,
            Set<String> grantTypes,
            Set<String> redirectUris,
            Set<String> corsOrigins
    ) {
        return new OAuthClientRegistration(
                "client-test",
                "Client Test",
                clientType,
                requireSecret,
                authMethod,
                requirePkce,
                false,
                null,
                null,
                "RS256",
                900,
                3600,
                900,
                null,
                Set.of("api:read"),
                grantTypes,
                redirectUris,
                Set.of(),
                corsOrigins
        );
    }
}
