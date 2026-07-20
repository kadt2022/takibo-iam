package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientGrantTypeEntity;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientLookupEntity;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientRedirectUriEntity;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientScopeEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientGrantTypeRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientLookupRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientPostLogoutRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientScopeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TakiboRegisteredClientRepositoryTest {

    private static final UUID ID    = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG   = UUID.fromString("674b889c-4d4e-47bd-bdf6-972dc84f1b49");
    private static final UUID SPACE = UUID.fromString("8932f9bc-0af0-4c64-94c8-abb0150c348b");

    @Mock private OAuth2ClientLookupRepository clients;
    @Mock private OAuth2ClientScopeRepository scopes;
    @Mock private OAuth2ClientGrantTypeRepository grantTypes;
    @Mock private OAuth2ClientRedirectUriRepository redirectUris;
    @Mock private OAuth2ClientPostLogoutRedirectUriRepository postLogoutRedirectUris;

    @InjectMocks private TakiboRegisteredClientRepository repository;

    @Test
    void given_db_client_when_find_by_client_id_then_maps_to_registered_client_with_scope_bound_settings() {
        OAuth2ClientLookupEntity entity = clientEntity("busa-finance", true, "$2a$12$hashvalue");
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));

        givenGrantTypes("client_credentials");
        givenScopes("api.read");

        RegisteredClient rc = repository.findByClientId("busa-finance");

        assertThat(rc).isNotNull();
        assertThat(rc.getClientId()).isEqualTo("busa-finance");
        assertThat(rc.getClientSecret()).isEqualTo("$2a$12$hashvalue");
        assertThat(rc.getScopes()).contains("api.read");
        assertThat(rc.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat((String) rc.getClientSettings().getSetting("takibo_scope_level")).isEqualTo("SPACE");
        assertThat((String) rc.getClientSettings().getSetting("takibo_tenant_source")).isEqualTo("oauth2_client");
        assertThat((String) rc.getClientSettings().getSetting("org_id")).isEqualTo(ORG.toString());
        assertThat((String) rc.getClientSettings().getSetting("space_id")).isEqualTo(SPACE.toString());
    }

    @Test
    void given_db_client_id_when_find_by_id_then_maps_to_registered_client() {
        OAuth2ClientLookupEntity entity = clientEntity("busa-finance", true, "$2a$12$hashvalue");
        when(clients.findById(ID)).thenReturn(Optional.of(entity));
        givenGrantTypes("client_credentials");
        givenScopes("api.read");

        RegisteredClient rc = repository.findById(ID.toString());

        assertThat(rc).isNotNull();
        assertThat(rc.getId()).isEqualTo(ID.toString());
        assertThat(rc.getClientId()).isEqualTo("busa-finance");
    }

    @Test
    void given_public_db_client_when_find_by_client_id_then_registered_client_has_no_secret() {
        OAuth2ClientLookupEntity entity = clientEntity("public-client", false, null);
        when(clients.findByClientId("public-client")).thenReturn(Optional.of(entity));
        givenGrantTypes("client_credentials");
        givenScopes("openid");

        RegisteredClient rc = repository.findByClientId("public-client");

        assertThat(rc).isNotNull();
        assertThat(rc.getClientSecret()).isNull();
        assertThat(rc.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(rc.getScopes()).contains("openid");
    }

    @Test
    void given_private_key_jwt_client_when_find_then_maps_verification_key_settings() {
        OAuth2ClientLookupEntity entity = mock(OAuth2ClientLookupEntity.class);
        when(entity.getId()).thenReturn(ID);
        when(entity.getClientId()).thenReturn("signed-client");
        when(entity.getOrgId()).thenReturn(ORG);
        when(entity.getSpaceId()).thenReturn(SPACE);
        when(entity.getTokenEndpointAuthMethod()).thenReturn("private_key_jwt");
        when(entity.getRequireClientSecret()).thenReturn(false);
        when(entity.getJwksUri()).thenReturn("https://keys.example/jwks.json");
        when(entity.getJwksJson()).thenReturn("{\"keys\":[]}");
        when(entity.getIdTokenSignedAlg()).thenReturn("RS256");
        when(clients.findByClientId("signed-client")).thenReturn(Optional.of(entity));
        givenGrantTypes("client_credentials");
        givenScopes("api.read");

        RegisteredClient rc = repository.findByClientId("signed-client");

        assertThat(rc).isNotNull();
        assertThat(rc.getClientSettings().getJwkSetUrl())
                .isEqualTo("https://keys.example/jwks.json");
        assertThat((String) rc.getClientSettings().getSetting(
                TakiboJwtClientAssertionDecoderFactory.JWK_SET_JSON_SETTING))
                .isEqualTo("{\"keys\":[]}");
        assertThat(rc.getClientSettings().getTokenEndpointAuthenticationSigningAlgorithm())
                .isEqualTo(SignatureAlgorithm.RS256);
    }

    @Test
    void given_authorization_code_client_with_redirect_uris_when_find_then_maps_redirect_uris() {
        OAuth2ClientLookupEntity entity = clientEntity("web-app", true, "$2a$12$hashvalue");
        when(clients.findByClientId("web-app")).thenReturn(Optional.of(entity));
        givenGrantTypes("authorization_code");
        givenScopes("openid");
        givenRedirectUris("https://app.takibo.io/callback");

        RegisteredClient rc = repository.findByClientId("web-app");

        assertThat(rc).isNotNull();
        assertThat(rc.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(rc.getRedirectUris()).contains("https://app.takibo.io/callback");
    }

    @Test
    void given_client_without_grant_types_when_find_by_client_id_then_it_is_treated_as_not_found() {
        OAuth2ClientLookupEntity entity = mock(OAuth2ClientLookupEntity.class);
        when(entity.getId()).thenReturn(ID);
        when(entity.getClientId()).thenReturn("broken-client");
        when(clients.findByClientId("broken-client")).thenReturn(Optional.of(entity));
        when(grantTypes.findByClientId(ID)).thenReturn(List.of());

        assertThat(repository.findByClientId("broken-client")).isNull();
    }

    @Test
    void given_unknown_client_when_find_by_client_id_then_returns_null() {
        when(clients.findByClientId("nope")).thenReturn(Optional.empty());
        assertThat(repository.findByClientId("nope")).isNull();
    }

    @Test
    void given_registered_client_when_save_then_throws_read_only_exception() {
        RegisteredClient client = RegisteredClient.withId(ID.toString())
                .clientId("busa-finance")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();

        assertThatThrownBy(() -> repository.save(client))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("management-service");
    }

    private OAuth2ClientLookupEntity clientEntity(String clientId, boolean requireClientSecret, String secretHash) {
        OAuth2ClientLookupEntity entity = mock(OAuth2ClientLookupEntity.class);
        when(entity.getId()).thenReturn(ID);
        when(entity.getClientId()).thenReturn(clientId);
        when(entity.getOrgId()).thenReturn(ORG);
        when(entity.getSpaceId()).thenReturn(SPACE);
        when(entity.getTokenEndpointAuthMethod()).thenReturn(requireClientSecret ? "client_secret_basic" : "none");
        when(entity.getRequireClientSecret()).thenReturn(requireClientSecret);
        if (requireClientSecret) {
            when(entity.getClientSecretHash()).thenReturn(secretHash);
        }
        return entity;
    }

    private void givenGrantTypes(String... values) {
        List<OAuth2ClientGrantTypeEntity> grants = List.of(values).stream()
                .map(value -> {
                    OAuth2ClientGrantTypeEntity grant = mock(OAuth2ClientGrantTypeEntity.class);
                    when(grant.getGrantType()).thenReturn(value);
                    return grant;
                })
                .toList();
        when(grantTypes.findByClientId(ID)).thenReturn(grants);
    }

    private void givenScopes(String... values) {
        List<OAuth2ClientScopeEntity> clientScopes = List.of(values).stream()
                .map(value -> {
                    OAuth2ClientScopeEntity scope = mock(OAuth2ClientScopeEntity.class);
                    when(scope.getScope()).thenReturn(value);
                    return scope;
                })
                .toList();
        when(scopes.findByClientId(ID)).thenReturn(clientScopes);
    }

    private void givenRedirectUris(String... values) {
        List<OAuth2ClientRedirectUriEntity> uris = List.of(values).stream()
                .map(value -> {
                    OAuth2ClientRedirectUriEntity uri = mock(OAuth2ClientRedirectUriEntity.class);
                    when(uri.getUri()).thenReturn(value);
                    return uri;
                })
                .toList();
        when(redirectUris.findByClientId(ID)).thenReturn(uris);
    }
}
