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
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void toEntity_withoutSpace_neverAsksForSpaceReference() {
        OAuthClientJpaMapper generated = new OAuthClientJpaMapperImpl();
        UUID orgId = UUID.randomUUID();
        SpaceRef refusesNull = id -> {
            throw new IllegalArgumentException("getReference(SpaceEntity, " + id + ")");
        };

        OAuth2ClientEntity organizationClient = generated.toEntity(
                OAuthClient.create(orgId, null, "org-client", "Org Client", ClientType.CONFIDENTIAL), refusesNull);
        OAuth2ClientEntity platformClient = generated.toEntity(
                OAuthClient.create(null, null, "platform-client", "Platform Client", ClientType.CONFIDENTIAL), refusesNull);

        assertThat(organizationClient.getOrgId()).isEqualTo(orgId);
        assertThat(organizationClient.getSpaceId()).isNull();
        assertThat(organizationClient.getSpace()).isNull();
        assertThat(platformClient.getOrgId()).isNull();
        assertThat(platformClient.getSpaceId()).isNull();
        assertThat(platformClient.getSpace()).isNull();
    }

    @Test
    void toEntity_withSpace_referencesThatSpace() {
        OAuthClientJpaMapper generated = new OAuthClientJpaMapperImpl();
        UUID spaceId = UUID.randomUUID();
        SpaceEntity reference = new SpaceEntity();

        OAuth2ClientEntity entity = generated.toEntity(
                OAuthClient.create(UUID.randomUUID(), SpaceId.of(spaceId), "space-client", "Space Client",
                        ClientType.CONFIDENTIAL),
                id -> {
                    assertThat(id).isEqualTo(spaceId);
                    return reference;
                });

        assertThat(entity.getSpaceId()).isEqualTo(spaceId);
        assertThat(entity.getSpace()).isSameAs(reference);
    }

    @Test
    void toEntity_transposesEveryScalarOfTheDomainClient() {
        OAuthClientJpaMapper generated = new OAuthClientJpaMapperImpl();
        OAuthClient client = fullClient();
        Instant before = Instant.now();

        OAuth2ClientEntity entity = generated.toEntity(client, id -> new SpaceEntity());

        assertThat(entity.getId()).isEqualTo(client.getId().getValue());
        assertThat(entity.getOrgId()).isEqualTo(client.getOrgId());
        assertThat(entity.getSpaceId()).isEqualTo(client.getSpaceId().value());
        assertThat(entity.getClientId()).isEqualTo("partner-portal");
        assertThat(entity.getClientName()).isEqualTo("Partner Portal");
        assertThat(entity.getClientType()).isEqualTo(OAuth2ClientEntity.ClientType.PUBLIC);
        assertThat(entity.getRequireClientSecret()).isFalse();
        assertThat(entity.getClientSecretHash()).isEqualTo(SECRET_HASH);
        assertThat(entity.getClientSecretExpiresAt()).isEqualTo(SECRET_EXPIRY);
        assertThat(entity.getTokenEndpointAuthMethod()).isEqualTo("private_key_jwt");
        assertThat(entity.getRequirePkce()).isTrue();
        assertThat(entity.getRequireConsent()).isTrue();
        assertThat(entity.getJwksUri()).isEqualTo("https://keys.example/jwks");
        assertThat(entity.getJwksJson()).isEqualTo(JWKS_JSON);
        assertThat(entity.getIdTokenSignedAlg()).isEqualTo("ES256");
        assertThat(entity.getAccessTokenTtlSeconds()).isEqualTo(600);
        assertThat(entity.getRefreshTokenTtlSeconds()).isEqualTo(7200);
        assertThat(entity.getIdTokenTtlSeconds()).isEqualTo(300);
        assertThat(entity.getAdditionalSettings()).containsExactly(Map.entry("theme", "dark"));
        assertThat(entity.getCreatedAt()).isEqualTo(client.getCreatedAt());
        assertThat(entity.getUpdatedAt()).isEqualTo(client.getUpdatedAt());
        assertThat(entity.getVersion()).isEqualTo(3L);
        assertThat(entity.getClientIdIssuedAt()).isBetween(before, Instant.now());
        assertThat(entity.getSecretHistory()).isEmpty();
    }

    @Test
    void toEntity_attachesEveryChildCollectionToTheNewClientRow() {
        OAuthClientJpaMapper generated = new OAuthClientJpaMapperImpl();
        OAuthClient client = fullClient();

        OAuth2ClientEntity entity = generated.toEntity(client, id -> new SpaceEntity());
        UUID rowId = client.getId().getValue();

        assertThat(entity.getScopes()).extracting(OAuth2ClientScopeEntity::getScope)
                .containsExactlyInAnyOrder("openid", "api:read");
        assertThat(entity.getGrantTypes()).extracting(OAuth2ClientGrantTypeEntity::getGrantType)
                .containsExactlyInAnyOrder("authorization_code", "refresh_token");
        assertThat(entity.getRedirectUris()).extracting(OAuth2ClientRedirectUriEntity::getUri)
                .containsExactly("https://partner.example/callback");
        assertThat(entity.getPostLogoutRedirectUris())
                .extracting(OAuth2ClientPostLogoutRedirectUriEntity::getUri)
                .containsExactly("https://partner.example/bye");
        assertThat(entity.getCorsOrigins()).extracting(OAuth2ClientCorsOriginEntity::getOrigin)
                .containsExactly("https://partner.example");

        assertThat(entity.getScopes()).allSatisfy(child -> {
            assertThat(child.getClientId()).isEqualTo(rowId);
            assertThat(child.getClient()).isSameAs(entity);
            assertThat(child.getId()).isNotNull().isNotEqualTo(rowId);
        });
        assertThat(entity.getScopes()).extracting(OAuth2ClientScopeEntity::getId)
                .doesNotHaveDuplicates();
        assertThat(entity.getGrantTypes()).allSatisfy(child -> {
            assertThat(child.getClientId()).isEqualTo(rowId);
            assertThat(child.getClient()).isSameAs(entity);
        });
        assertThat(entity.getRedirectUris()).allSatisfy(child -> {
            assertThat(child.getClientId()).isEqualTo(rowId);
            assertThat(child.getClient()).isSameAs(entity);
        });
        assertThat(entity.getPostLogoutRedirectUris()).allSatisfy(child -> {
            assertThat(child.getClientId()).isEqualTo(rowId);
            assertThat(child.getClient()).isSameAs(entity);
        });
        assertThat(entity.getCorsOrigins()).allSatisfy(child -> {
            assertThat(child.getClientId()).isEqualTo(rowId);
            assertThat(child.getClient()).isSameAs(entity);
        });
    }

    @Test
    void toDomain_restoresTheClientWithItsBoundaryAndChildren() {
        OAuthClientJpaMapper generated = new OAuthClientJpaMapperImpl();
        OAuth2ClientEntity entity = generated.toEntity(fullClient(), id -> {
            SpaceEntity space = new SpaceEntity();
            space.setId(id);
            return space;
        });
        OAuthClient original = fullClient();

        OAuthClient restored = generated.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getOrgId()).isEqualTo(original.getOrgId());
        assertThat(restored.getSpaceId()).isEqualTo(original.getSpaceId());
        assertThat(restored.getClientId()).isEqualTo("partner-portal");
        assertThat(restored.getClientName()).isEqualTo("Partner Portal");
        assertThat(restored.getClientType()).isEqualTo(ClientType.PUBLIC);
        assertThat(restored.isRequireClientSecret()).isFalse();
        assertThat(restored.getClientSecretHash()).isEqualTo(SECRET_HASH);
        assertThat(restored.getClientSecretExpiresAt()).isEqualTo(SECRET_EXPIRY);
        assertThat(restored.getTokenEndpointAuthMethod())
                .isEqualTo(TokenEndpointAuthMethod.private_key_jwt);
        assertThat(restored.isRequirePkce()).isTrue();
        assertThat(restored.isRequireConsent()).isTrue();
        assertThat(restored.getJwksUri()).isEqualTo("https://keys.example/jwks");
        assertThat(restored.getJwksJson()).isEqualTo(JWKS_JSON);
        assertThat(restored.getIdTokenSignedAlg()).isEqualTo("ES256");
        assertThat(restored.getAccessTokenTtlSeconds()).isEqualTo(600);
        assertThat(restored.getRefreshTokenTtlSeconds()).isEqualTo(7200);
        assertThat(restored.getIdTokenTtlSeconds()).isEqualTo(300);
        assertThat(restored.getAdditionalSettings()).containsExactly(Map.entry("theme", "dark"));
        assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(restored.getVersion()).isEqualTo(3L);
        assertThat(restored.getScopes()).containsExactlyInAnyOrder("openid", "api:read");
        assertThat(restored.getGrantTypes())
                .containsExactlyInAnyOrder("authorization_code", "refresh_token");
        assertThat(restored.getRedirectUris()).containsExactly("https://partner.example/callback");
        assertThat(restored.getPostLogoutRedirectUris()).containsExactly("https://partner.example/bye");
        assertThat(restored.getCorsOrigins()).containsExactly("https://partner.example");
    }

    @Test
    void toDomain_readsTheSpaceBoundaryFromTheSpaceAssociationNotFromTheColumn() {
        OAuthClientJpaMapper generated = new OAuthClientJpaMapperImpl();
        OAuth2ClientEntity entity = baseEntity();

        assertThat(entity.getSpaceId()).isNotNull();
        assertThat(generated.toDomain(entity).getSpaceId()).isNull();

        entity.setSpace(new SpaceEntity());
        assertThat(generated.toDomain(entity).getSpaceId()).isNull();
    }

    @Test
    void toDomain_keepsDomainDefaultsWhenNullableColumnsAreMissing() {
        OAuthClientJpaMapper generated = new OAuthClientJpaMapperImpl();
        OAuth2ClientEntity entity = baseEntity();
        entity.setRequireClientSecret(null);
        entity.setRequirePkce(null);
        entity.setRequireConsent(null);
        entity.setTokenEndpointAuthMethod(null);
        entity.setClientType(null);
        entity.setAdditionalSettings(null);

        OAuthClient restored = generated.toDomain(entity);

        assertThat(restored.isRequireClientSecret()).isFalse();
        assertThat(restored.isRequirePkce()).isFalse();
        assertThat(restored.isRequireConsent()).isFalse();
        assertThat(restored.getTokenEndpointAuthMethod()).isNull();
        assertThat(restored.getClientType()).isNull();
        assertThat(restored.getAdditionalSettings()).isEmpty();
        assertThat(restored.getScopes()).isEmpty();
    }

    @Test
    void toDomain_refusesAnUnknownStoredAuthenticationMethod() {
        OAuthClientJpaMapper generated = new OAuthClientJpaMapperImpl();
        OAuth2ClientEntity entity = baseEntity();
        entity.setTokenEndpointAuthMethod("tls_client_auth");

        assertThatThrownBy(() -> generated.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullInputsMapToNull() {
        OAuthClientJpaMapper generated = new OAuthClientJpaMapperImpl();

        assertThat(generated.toEntity(null, id -> new SpaceEntity())).isNull();
        assertThat(generated.toDomain(null)).isNull();
        assertThat(generated.map((OAuthClientId) null)).isNull();
        assertThat(generated.map((SpaceId) null)).isNull();
        assertThat(generated.map((TokenEndpointAuthMethod) null)).isNull();
        assertThat(generated.map((UUID) null)).isNull();
        assertThat(generated.map((String) null)).isNull();
    }

    @Test
    void applyDomainState_copiesScalarsAndClearsChildrenOfAnAbsentCollection() {
        OAuth2ClientEntity entity = baseEntity();
        UUID rowId = entity.getId();
        entity.getScopes().add(scope(entity, UUID.randomUUID(), "api:read"));
        OAuthClient domain = fullClient().toBuilder()
                .clientType(null)
                .scopes(null)
                .build();

        mapper.applyDomainState(domain, entity);

        assertThat(entity.getId()).isEqualTo(rowId);
        assertThat(entity.getClientId()).isEqualTo("partner-portal");
        assertThat(entity.getClientName()).isEqualTo("Partner Portal");
        assertThat(entity.getClientType()).isNull();
        assertThat(entity.getRequireClientSecret()).isFalse();
        assertThat(entity.getClientSecretHash()).isEqualTo(SECRET_HASH);
        assertThat(entity.getTokenEndpointAuthMethod()).isEqualTo("private_key_jwt");
        assertThat(entity.getRequirePkce()).isTrue();
        assertThat(entity.getRequireConsent()).isTrue();
        assertThat(entity.getJwksUri()).isEqualTo("https://keys.example/jwks");
        assertThat(entity.getJwksJson()).isEqualTo(JWKS_JSON);
        assertThat(entity.getIdTokenSignedAlg()).isEqualTo("ES256");
        assertThat(entity.getAccessTokenTtlSeconds()).isEqualTo(600);
        assertThat(entity.getRefreshTokenTtlSeconds()).isEqualTo(7200);
        assertThat(entity.getIdTokenTtlSeconds()).isEqualTo(300);
        assertThat(entity.getAdditionalSettings()).containsExactly(Map.entry("theme", "dark"));
        assertThat(entity.getScopes()).isEmpty();
        assertThat(entity.getGrantTypes()).hasSize(2).allSatisfy(child -> {
            assertThat(child.getClientId()).isEqualTo(rowId);
            assertThat(child.getClient()).isSameAs(entity);
        });
    }

    private static final String SECRET_HASH = "argon2id-hash";
    private static final Instant SECRET_EXPIRY = Instant.parse("2031-06-01T00:00:00Z");
    private static final String JWKS_JSON = "{\"keys\":[]}";

    private static OAuthClient fullClient() {
        return OAuthClient.builder()
                .id(OAuthClientId.of(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .orgId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .spaceId(SpaceId.of(UUID.fromString("33333333-3333-3333-3333-333333333333")))
                .clientId("partner-portal")
                .clientName("Partner Portal")
                .clientType(ClientType.PUBLIC)
                .requireClientSecret(false)
                .clientSecretHash(SECRET_HASH)
                .clientSecretExpiresAt(SECRET_EXPIRY)
                .tokenEndpointAuthMethod(TokenEndpointAuthMethod.private_key_jwt)
                .requirePkce(true)
                .requireConsent(true)
                .jwksUri("https://keys.example/jwks")
                .jwksJson(JWKS_JSON)
                .idTokenSignedAlg("ES256")
                .accessTokenTtlSeconds(600)
                .refreshTokenTtlSeconds(7200)
                .idTokenTtlSeconds(300)
                .scopes(Set.of("openid", "api:read"))
                .grantTypes(Set.of("authorization_code", "refresh_token"))
                .redirectUris(Set.of("https://partner.example/callback"))
                .postLogoutRedirectUris(Set.of("https://partner.example/bye"))
                .corsOrigins(Set.of("https://partner.example"))
                .additionalSettings(Map.of("theme", "dark"))
                .createdAt(Instant.parse("2030-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2030-02-01T00:00:00Z"))
                .version(3L)
                .build();
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
                .id(id).clientId(client.getId())
                .client(client).scope(value).build();
    }

    private static OAuth2ClientGrantTypeEntity grant(OAuth2ClientEntity client, UUID id, String value) {
        return OAuth2ClientGrantTypeEntity.builder()
                .id(id).clientId(client.getId())
                .client(client).grantType(value).build();
    }

    private static OAuth2ClientRedirectUriEntity redirect(OAuth2ClientEntity client, UUID id, String value) {
        return OAuth2ClientRedirectUriEntity.builder()
                .id(id).clientId(client.getId())
                .client(client).uri(value).build();
    }

    private static OAuth2ClientPostLogoutRedirectUriEntity postLogout(OAuth2ClientEntity client, UUID id, String value) {
        return OAuth2ClientPostLogoutRedirectUriEntity.builder()
                .id(id).clientId(client.getId())
                .client(client).uri(value).build();
    }

    private static OAuth2ClientCorsOriginEntity cors(OAuth2ClientEntity client, UUID id, String value) {
        return OAuth2ClientCorsOriginEntity.builder()
                .id(id).clientId(client.getId())
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
