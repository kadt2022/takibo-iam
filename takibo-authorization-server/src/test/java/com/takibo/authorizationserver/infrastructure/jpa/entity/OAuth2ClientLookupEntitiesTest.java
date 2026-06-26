package com.takibo.authorizationserver.infrastructure.jpa.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2ClientLookupEntitiesTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SPACE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void given_oauth2_client_lookup_entity_fields_when_getters_are_called_then_values_are_returned() {
        OAuth2ClientLookupEntity entity = new OAuth2ClientLookupEntity();
        ReflectionTestUtils.setField(entity, "id", ID);
        ReflectionTestUtils.setField(entity, "orgId", ORG_ID);
        ReflectionTestUtils.setField(entity, "spaceId", SPACE_ID);
        ReflectionTestUtils.setField(entity, "clientId", "finance-app");
        ReflectionTestUtils.setField(entity, "clientType", OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL);
        ReflectionTestUtils.setField(entity, "requirePkce", true);
        ReflectionTestUtils.setField(entity, "requireClientSecret", true);
        ReflectionTestUtils.setField(entity, "clientSecretHash", "{bcrypt}hash");
        ReflectionTestUtils.setField(entity, "tokenEndpointAuthMethod", "client_secret_basic");

        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getOrgId()).isEqualTo(ORG_ID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_ID);
        assertThat(entity.getClientId()).isEqualTo("finance-app");
        assertThat(entity.getClientType()).isEqualTo(OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL);
        assertThat(entity.getRequirePkce()).isTrue();
        assertThat(entity.getRequireClientSecret()).isTrue();
        assertThat(entity.getClientSecretHash()).isEqualTo("{bcrypt}hash");
        assertThat(entity.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
    }

    @Test
    void given_oauth2_client_grant_type_entity_fields_when_getters_are_called_then_values_are_returned() {
        OAuth2ClientGrantTypeEntity entity = new OAuth2ClientGrantTypeEntity();
        ReflectionTestUtils.setField(entity, "id", ID);
        ReflectionTestUtils.setField(entity, "clientId", CLIENT_ID);
        ReflectionTestUtils.setField(entity, "grantType", "client_credentials");

        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(entity.getGrantType()).isEqualTo("client_credentials");
    }

    @Test
    void given_oauth2_client_scope_entity_fields_when_getters_are_called_then_values_are_returned() {
        OAuth2ClientScopeEntity entity = new OAuth2ClientScopeEntity();
        ReflectionTestUtils.setField(entity, "id", ID);
        ReflectionTestUtils.setField(entity, "clientId", CLIENT_ID);
        ReflectionTestUtils.setField(entity, "scope", "api.read");

        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(entity.getScope()).isEqualTo("api.read");
    }
}
