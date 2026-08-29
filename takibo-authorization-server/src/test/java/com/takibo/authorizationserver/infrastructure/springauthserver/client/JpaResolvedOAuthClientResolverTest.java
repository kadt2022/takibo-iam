package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ClientType;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientGrantTypeEntity;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientLookupEntity;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientPostLogoutRedirectUriEntity;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaResolvedOAuthClientResolverTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG = UUID.fromString("674b889c-4d4e-47bd-bdf6-972dc84f1b49");
    private static final UUID SPACE = UUID.fromString("8932f9bc-0af0-4c64-94c8-abb0150c348b");

    @Mock private OAuth2ClientLookupRepository clients;
    @Mock private OAuth2ClientGrantTypeRepository grantTypes;
    @Mock private OAuth2ClientScopeRepository scopes;
    @Mock private OAuth2ClientRedirectUriRepository redirectUris;
    @Mock private OAuth2ClientPostLogoutRedirectUriRepository postLogoutRedirectUris;

    @InjectMocks private JpaResolvedOAuthClientResolver resolver;

    @Test
    void given_a_db_client_then_it_resolves_as_space() {
        OAuth2ClientLookupEntity entity = clientEntity(true, "$2a$12$hashvalue");
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));
        givenGrantTypes("client_credentials");
        givenScopes("api.read");
        givenRedirectUris("https://busa.example/callback");
        givenPostLogoutRedirectUris("https://busa.example/logout");

        ResolvedOAuthClient client = resolver.resolve("busa-finance").orElseThrow();

        assertThat(client.registeredClientId()).isEqualTo(ID.toString());
        assertThat(client.clientId()).isEqualTo("busa-finance");
        assertThat(client.plan()).isEqualTo(ClientPlan.SPACE);
        assertThat(client.orgId()).isEqualTo(ORG);
        assertThat(client.spaceId()).isEqualTo(SPACE);
        assertThat(client.clientType()).isEqualTo(ClientType.CONFIDENTIAL);
        assertThat(client.clientSecretHash()).isEqualTo("$2a$12$hashvalue");
        assertThat(client.scopes()).containsExactly("api.read");
        assertThat(client.grantTypes()).containsExactly("client_credentials");
        assertThat(client.redirectUris()).containsExactly("https://busa.example/callback");
        assertThat(client.postLogoutRedirectUris()).containsExactly("https://busa.example/logout");
    }

    @Test
    void given_a_public_client_without_required_secret_then_it_resolves_as_public() {
        OAuth2ClientLookupEntity entity = clientEntity(false, null);
        when(clients.findByClientId("spa-client")).thenReturn(Optional.of(entity));
        givenGrantTypes("authorization_code");

        ResolvedOAuthClient client = resolver.resolve("spa-client").orElseThrow();

        assertThat(client.clientType()).isEqualTo(ClientType.PUBLIC);
        assertThat(client.requireClientSecret()).isFalse();
    }

    @Test
    void given_an_unknown_client_id_then_nothing_resolves() {
        when(clients.findByClientId("ghost")).thenReturn(Optional.empty());

        assertThat(resolver.resolve("ghost")).isEmpty();
    }

    @Test
    void given_a_client_with_no_grant_type_then_it_is_treated_as_not_found() {
        // Entite minimale : la resolution s'arrete au premier champ lu (l'id, pour chercher
        // les grant types), le reste ne serait jamais consulte.
        OAuth2ClientLookupEntity entity = mock(OAuth2ClientLookupEntity.class);
        when(entity.getId()).thenReturn(ID);
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));
        when(grantTypes.findByClientId(ID)).thenReturn(List.of());

        assertThat(resolver.resolve("busa-finance")).isEmpty();
    }

    @Test
    void given_a_client_requiring_a_secret_without_a_hash_then_it_is_treated_as_not_found() {
        // Configuration incoherente en base : le constructeur de ResolvedOAuthClient la
        // refuse, la resolution doit l'absorber plutot que de la laisser remonter.
        OAuth2ClientLookupEntity entity = clientEntity(true, null);
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));
        givenGrantTypes("client_credentials");

        assertThat(resolver.resolve("busa-finance")).isEmpty();
    }

    private OAuth2ClientLookupEntity clientEntity(boolean requireClientSecret, String secretHash) {
        OAuth2ClientLookupEntity entity = mock(OAuth2ClientLookupEntity.class);
        when(entity.getId()).thenReturn(ID);
        when(entity.getClientId()).thenReturn(requireClientSecret ? "busa-finance" : "spa-client");
        when(entity.getOrgId()).thenReturn(ORG);
        when(entity.getSpaceId()).thenReturn(SPACE);
        when(entity.getTokenEndpointAuthMethod())
                .thenReturn(requireClientSecret ? "client_secret_basic" : "none");
        when(entity.getRequireClientSecret()).thenReturn(requireClientSecret);
        when(entity.getClientSecretHash()).thenReturn(secretHash);
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

    private void givenPostLogoutRedirectUris(String... values) {
        List<OAuth2ClientPostLogoutRedirectUriEntity> uris = List.of(values).stream()
                .map(value -> {
                    OAuth2ClientPostLogoutRedirectUriEntity uri =
                            mock(OAuth2ClientPostLogoutRedirectUriEntity.class);
                    when(uri.getUri()).thenReturn(value);
                    return uri;
                })
                .toList();
        when(postLogoutRedirectUris.findByClientId(ID)).thenReturn(uris);
    }
}
