package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.Secrets;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.normalization.OAuthClientCollectionsNormalizer;
import com.takibo.managementservice.domain.policy.OAuthClientAuthenticationPolicy;
import com.takibo.managementservice.domain.policy.OAuthClientCredentialsProfilePolicy;
import com.takibo.managementservice.domain.policy.OAuthClientGrantPolicy;
import com.takibo.managementservice.domain.validation.OAuthClientConfigurationValidator;
import com.takibo.managementservice.domain.vo.SpaceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuthClientRegistrationDomainServiceTest {

    @Mock
    private OAuthClientConfigurationValidator configurationValidator;

    @Spy
    private OAuthClientCredentialsProfilePolicy credentialsProfilePolicy;

    @Spy
    private OAuthClientAuthenticationPolicy authenticationPolicy;

    @Spy
    private OAuthClientCollectionsNormalizer collectionsNormalizer;

    @Spy
    private OAuthClientGrantPolicy grantPolicy;

    @InjectMocks
    private OAuthClientRegistrationDomainService service;

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
