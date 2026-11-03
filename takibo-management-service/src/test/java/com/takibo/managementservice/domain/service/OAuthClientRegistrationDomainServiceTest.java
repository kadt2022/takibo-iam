package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.exception.InvalidRedirectUriException;
import com.takibo.managementservice.domain.exception.PublicClientAuthMethodNotNoneException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
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

    @Test
    void a_client_credentials_grant_written_in_another_case_is_still_refused_for_a_public_client() {
        // La politique client_credentials ne promeut en CONFIDENTIAL que la
        // valeur exacte "client_credentials" ; la variante en majuscules n'est
        // pas promue mais reste refusée plus loin (fail-closed).
        OAuthClientRegistration registration = registration(
                ClientType.PUBLIC,
                TokenEndpointAuthMethod.none,
                false,
                false,
                Set.of(" CLIENT_CREDENTIALS "),
                Set.of(),
                Set.of()
        );

        assertThatThrownBy(() -> service.prepareRegistration(registration))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage("client_credentials requires clientType=CONFIDENTIAL");
    }

    @Test
    void the_authentication_policy_refuses_a_public_client_before_grant_rules_run() {
        OAuthClientRegistration registration = registration(
                ClientType.PUBLIC,
                TokenEndpointAuthMethod.client_secret_basic,
                false,
                true,
                Set.of("authorization_code"),
                Set.of("https://app.example/callback"),
                Set.of("https://app.example")
        );

        assertThatThrownBy(() -> service.prepareRegistration(registration))
                .isInstanceOf(PublicClientAuthMethodNotNoneException.class);
        verify(grantPolicy, never()).validateGrantProfile(any(), any(), any(), anyBoolean());
    }

    @Test
    void a_public_client_without_explicit_pkce_is_planned_with_pkce() {
        OAuthClientRegistration registration = registration(
                ClientType.PUBLIC,
                null,
                null,
                null,
                Set.of("authorization_code"),
                Set.of("https://app.example/callback"),
                Set.of("https://app.example")
        );

        var plan = service.prepareRegistration(registration);

        assertThat(plan.authMethod()).isEqualTo(TokenEndpointAuthMethod.none);
        assertThat(plan.requirePkce()).isTrue();
        assertThat(plan.requireSecret()).isFalse();
    }

    @Test
    void a_private_key_jwt_web_client_is_planned_without_a_shared_secret() {
        OAuthClientRegistration registration = registration(
                ClientType.CONFIDENTIAL,
                TokenEndpointAuthMethod.private_key_jwt,
                false,
                null,
                Set.of("authorization_code"),
                Set.of("https://app.example/callback"),
                Set.of()
        );

        var plan = service.prepareRegistration(registration);

        assertThat(plan.authMethod()).isEqualTo(TokenEndpointAuthMethod.private_key_jwt);
        assertThat(plan.requirePkce()).isFalse();
        assertThat(plan.requireSecret()).isFalse();
    }

    @Test
    void a_redirect_uri_that_is_not_https_is_refused_while_normalizing_collections() {
        OAuthClientRegistration registration = registration(
                ClientType.CONFIDENTIAL,
                TokenEndpointAuthMethod.client_secret_basic,
                true,
                null,
                Set.of("authorization_code"),
                Set.of("http://app.example/callback"),
                Set.of()
        );

        assertThatThrownBy(() -> service.prepareRegistration(registration))
                .isInstanceOf(InvalidRedirectUriException.class)
                .hasMessageContaining("http://app.example/callback");
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
