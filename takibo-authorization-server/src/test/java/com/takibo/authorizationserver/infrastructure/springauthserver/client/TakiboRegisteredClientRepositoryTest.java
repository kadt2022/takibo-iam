package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientGrantTypeEntity;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientLookupEntity;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientScopeEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientGrantTypeRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientLookupRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientScopeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks private TakiboRegisteredClientRepository repository;

    @Test
    void maps_db_client_to_registered_client_with_scope_bound_settings() {
        OAuth2ClientLookupEntity entity = mock(OAuth2ClientLookupEntity.class);
        when(entity.getId()).thenReturn(ID);
        when(entity.getClientId()).thenReturn("busa-finance");
        when(entity.getOrgId()).thenReturn(ORG);
        when(entity.getSpaceId()).thenReturn(SPACE);
        when(entity.getTokenEndpointAuthMethod()).thenReturn("client_secret_basic");
        when(entity.getRequireClientSecret()).thenReturn(true);
        when(entity.getClientSecretHash()).thenReturn("$2a$12$hashvalue");
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));

        OAuth2ClientGrantTypeEntity grant = mock(OAuth2ClientGrantTypeEntity.class);
        when(grant.getGrantType()).thenReturn("client_credentials");
        when(grantTypes.findByClientId(ID)).thenReturn(List.of(grant));

        OAuth2ClientScopeEntity scope = mock(OAuth2ClientScopeEntity.class);
        when(scope.getScope()).thenReturn("api.read");
        when(scopes.findByClientId(ID)).thenReturn(List.of(scope));

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
    void client_without_grant_types_is_treated_as_not_found() {
        OAuth2ClientLookupEntity entity = mock(OAuth2ClientLookupEntity.class);
        when(entity.getId()).thenReturn(ID);
        when(entity.getClientId()).thenReturn("broken-client");
        when(clients.findByClientId("broken-client")).thenReturn(Optional.of(entity));
        when(grantTypes.findByClientId(ID)).thenReturn(List.of());

        assertThat(repository.findByClientId("broken-client")).isNull();
    }

    @Test
    void unknown_client_returns_null() {
        when(clients.findByClientId("nope")).thenReturn(Optional.empty());
        assertThat(repository.findByClientId("nope")).isNull();
    }
}
