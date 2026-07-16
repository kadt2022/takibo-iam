package com.takibo.managementservice.infrastructure.jpa.mapper;

import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.vo.OAuthClientId;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.infrastructure.entity.OAuth2ClientCorsOriginEntity;
import com.takibo.managementservice.infrastructure.entity.OAuth2ClientEntity;
import com.takibo.managementservice.infrastructure.entity.OAuth2ClientGrantTypeEntity;
import com.takibo.managementservice.infrastructure.entity.OAuth2ClientPostLogoutRedirectUriEntity;
import com.takibo.managementservice.infrastructure.entity.OAuth2ClientRedirectUriEntity;
import com.takibo.managementservice.infrastructure.entity.OAuth2ClientScopeEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthClientJpaMapperTest {

    private final OAuthClientJpaMapper mapper = new TestMapper();

    @Test
    void applyDomainState_duringSecretRotation_preservesExistingChildrenAndDoesNotDuplicate() {
        OAuth2ClientEntity entity = baseEntity();
        UUID scopeId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        UUID redirectId = UUID.randomUUID();
        UUID postLogoutId = UUID.randomUUID();
        UUID corsId = UUID.randomUUID();
        entity.getScopes().add(scope(entity, scopeId, "api:read"));
        entity.getGrantTypes().add(grant(entity, grantId, "client_credentials"));
        entity.getRedirectUris().add(redirect(entity, redirectId, "https://app.example/callback"));
        entity.getPostLogoutRedirectUris().add(postLogout(entity, postLogoutId, "https://app.example/logout"));
        entity.getCorsOrigins().add(cors(entity, corsId, "https://app.example"));

        Instant expiresAt = Instant.parse("2035-01-01T00:00:00Z");
        OAuthClient domain = domainFor(entity)
                .toBuilder()
                .clientSecretHash("new-hash")
                .clientSecretExpiresAt(expiresAt)
                .build();

        mapper.applyDomainState(domain, entity);

        assertThat(entity.getClientSecretHash()).isEqualTo("new-hash");
        assertThat(entity.getClientSecretExpiresAt()).isEqualTo(expiresAt);
        assertThat(entity.getScopes()).singleElement().extracting(OAuth2ClientScopeEntity::getId).isEqualTo(scopeId);
        assertThat(entity.getGrantTypes()).singleElement().extracting(OAuth2ClientGrantTypeEntity::getId).isEqualTo(grantId);
        assertThat(entity.getRedirectUris()).singleElement().extracting(OAuth2ClientRedirectUriEntity::getId).isEqualTo(redirectId);
        assertThat(entity.getPostLogoutRedirectUris()).singleElement()
                .extracting(OAuth2ClientPostLogoutRedirectUriEntity::getId).isEqualTo(postLogoutId);
        assertThat(entity.getCorsOrigins()).singleElement().extracting(OAuth2ClientCorsOriginEntity::getId).isEqualTo(corsId);
    }

    @Test
    void applyDomainState_synchronizesChildrenByDifference() {
        OAuth2ClientEntity entity = baseEntity();
        UUID keptScopeId = UUID.randomUUID();
        UUID keptGrantId = UUID.randomUUID();
        UUID keptRedirectId = UUID.randomUUID();
        UUID keptPostLogoutId = UUID.randomUUID();
        UUID keptCorsId = UUID.randomUUID();
        entity.getScopes().add(scope(entity, keptScopeId, "api:read"));
        entity.getScopes().add(scope(entity, UUID.randomUUID(), "api:old"));
        entity.getGrantTypes().add(grant(entity, keptGrantId, "client_credentials"));
        entity.getGrantTypes().add(grant(entity, UUID.randomUUID(), "authorization_code"));
        entity.getRedirectUris().add(redirect(entity, keptRedirectId, "https://app.example/callback"));
        entity.getRedirectUris().add(redirect(entity, UUID.randomUUID(), "https://old.example/callback"));
        entity.getPostLogoutRedirectUris().add(postLogout(entity, keptPostLogoutId, "https://app.example/logout"));
        entity.getPostLogoutRedirectUris().add(postLogout(entity, UUID.randomUUID(), "https://old.example/logout"));
        entity.getCorsOrigins().add(cors(entity, keptCorsId, "https://app.example"));
        entity.getCorsOrigins().add(cors(entity, UUID.randomUUID(), "https://old.example"));

        OAuthClient domain = domainFor(entity).toBuilder()
                .scopes(Set.of("api:read", "api:write"))
                .grantTypes(Set.of("client_credentials", "refresh_token"))
                .redirectUris(Set.of("https://app.example/callback", "https://new.example/callback"))
                .postLogoutRedirectUris(Set.of("https://app.example/logout", "https://new.example/logout"))
                .corsOrigins(Set.of("https://app.example", "https://new.example"))
                .build();

        mapper.applyDomainState(domain, entity);

        assertThat(entity.getScopes()).extracting(OAuth2ClientScopeEntity::getScope)
                .containsExactlyInAnyOrder("api:read", "api:write");
        assertThat(entity.getScopes()).filteredOn(e -> "api:read".equals(e.getScope()))
                .singleElement().extracting(OAuth2ClientScopeEntity::getId).isEqualTo(keptScopeId);
        assertThat(entity.getGrantTypes()).extracting(OAuth2ClientGrantTypeEntity::getGrantType)
                .containsExactlyInAnyOrder("client_credentials", "refresh_token");
        assertThat(entity.getGrantTypes()).filteredOn(e -> "client_credentials".equals(e.getGrantType()))
                .singleElement().extracting(OAuth2ClientGrantTypeEntity::getId).isEqualTo(keptGrantId);
        assertThat(entity.getRedirectUris()).extracting(OAuth2ClientRedirectUriEntity::getUri)
                .containsExactlyInAnyOrder("https://app.example/callback", "https://new.example/callback");
        assertThat(entity.getRedirectUris()).filteredOn(e -> "https://app.example/callback".equals(e.getUri()))
                .singleElement().extracting(OAuth2ClientRedirectUriEntity::getId).isEqualTo(keptRedirectId);
        assertThat(entity.getPostLogoutRedirectUris()).extracting(OAuth2ClientPostLogoutRedirectUriEntity::getUri)
                .containsExactlyInAnyOrder("https://app.example/logout", "https://new.example/logout");
        assertThat(entity.getPostLogoutRedirectUris()).filteredOn(e -> "https://app.example/logout".equals(e.getUri()))
                .singleElement().extracting(OAuth2ClientPostLogoutRedirectUriEntity::getId).isEqualTo(keptPostLogoutId);
        assertThat(entity.getCorsOrigins()).extracting(OAuth2ClientCorsOriginEntity::getOrigin)
                .containsExactlyInAnyOrder("https://app.example", "https://new.example");
        assertThat(entity.getCorsOrigins()).filteredOn(e -> "https://app.example".equals(e.getOrigin()))
                .singleElement().extracting(OAuth2ClientCorsOriginEntity::getId).isEqualTo(keptCorsId);
    }

    private static OAuth2ClientEntity baseEntity() {
        return OAuth2ClientEntity.builder()
                .id(UUID.randomUUID())
                .orgId(UUID.randomUUID())
                .spaceId(UUID.randomUUID())
                .clientId("machine-client")
                .clientName("Machine Client")
                .clientType(OAuth2ClientEntity.ClientType.CONFIDENTIAL)
                .requireClientSecret(true)
                .tokenEndpointAuthMethod("client_secret_basic")
                .requirePkce(false)
                .requireConsent(false)
                .build();
    }

    private static OAuthClient domainFor(OAuth2ClientEntity entity) {
        return OAuthClient.builder()
                .id(OAuthClientId.of(entity.getId()))
                .orgId(entity.getOrgId())
                .spaceId(SpaceId.of(entity.getSpaceId()))
                .clientId(entity.getClientId())
                .clientName(entity.getClientName())
                .clientType(ClientType.CONFIDENTIAL)
                .requireClientSecret(true)
                .tokenEndpointAuthMethod(TokenEndpointAuthMethod.client_secret_basic)
                .requirePkce(false)
                .requireConsent(false)
                .scopes(Set.of("api:read"))
                .grantTypes(Set.of("client_credentials"))
                .redirectUris(Set.of("https://app.example/callback"))
                .postLogoutRedirectUris(Set.of("https://app.example/logout"))
                .corsOrigins(Set.of("https://app.example"))
                .build();
    }

    private static OAuth2ClientScopeEntity scope(OAuth2ClientEntity client, UUID id, String value) {
        return OAuth2ClientScopeEntity.builder()
                .id(id).orgId(client.getOrgId()).spaceId(client.getSpaceId()).clientId(client.getId())
                .client(client).scope(value).build();
    }

    private static OAuth2ClientGrantTypeEntity grant(OAuth2ClientEntity client, UUID id, String value) {
        return OAuth2ClientGrantTypeEntity.builder()
                .id(id).orgId(client.getOrgId()).spaceId(client.getSpaceId()).clientId(client.getId())
                .client(client).grantType(value).build();
    }

    private static OAuth2ClientRedirectUriEntity redirect(OAuth2ClientEntity client, UUID id, String value) {
        return OAuth2ClientRedirectUriEntity.builder()
                .id(id).orgId(client.getOrgId()).spaceId(client.getSpaceId()).clientId(client.getId())
                .client(client).uri(value).build();
    }

    private static OAuth2ClientPostLogoutRedirectUriEntity postLogout(OAuth2ClientEntity client, UUID id, String value) {
        return OAuth2ClientPostLogoutRedirectUriEntity.builder()
                .id(id).orgId(client.getOrgId()).spaceId(client.getSpaceId()).clientId(client.getId())
                .client(client).uri(value).build();
    }

    private static OAuth2ClientCorsOriginEntity cors(OAuth2ClientEntity client, UUID id, String value) {
        return OAuth2ClientCorsOriginEntity.builder()
                .id(id).orgId(client.getOrgId()).spaceId(client.getSpaceId()).clientId(client.getId())
                .client(client).origin(value).build();
    }

    private static final class TestMapper implements OAuthClientJpaMapper {
        @Override
        public OAuth2ClientEntity toEntity(OAuthClient client, SpaceRef spaceRef) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OAuthClient toDomain(OAuth2ClientEntity e) {
            throw new UnsupportedOperationException();
        }
    }
}
