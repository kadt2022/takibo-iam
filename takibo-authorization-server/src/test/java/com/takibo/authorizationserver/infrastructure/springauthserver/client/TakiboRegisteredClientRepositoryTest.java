package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ClientType;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientContextHolder;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientGrantTypeEntity;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientLookupEntity;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientScopeEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientGrantTypeRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientLookupRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientPostLogoutRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientScopeRepository;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.TakiboTokenClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code findByClientId} : reconstruit le {@link RegisteredClient} uniquement depuis le
 * {@link ResolvedOAuthClient} rendu par {@link ResolvedOAuthClientResolver} (TAS-GRANTS-01) —
 * aucun accès direct aux cinq dépôts TMS, contrairement à {@code findById}, que le port ne
 * peut pas servir (il résout par {@code client_id} public, jamais par identifiant technique).
 */
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
    @Mock private ResolvedOAuthClientResolver resolvedOAuthClientResolver;

    @InjectMocks private TakiboRegisteredClientRepository repository;

    @AfterEach
    void clearResolvedClientContext() {
        ResolvedOAuthClientContextHolder.clear();
    }

    // ---------- findByClientId : une seule resolution par requete ----------

    @Test
    void given_a_resolved_client_already_in_the_request_context_when_find_by_client_id_then_it_is_reused() {
        ResolvedOAuthClientContextHolder.set(aClient("busa-finance").build());

        RegisteredClient rc = repository.findByClientId("busa-finance");

        assertThat(rc).isNotNull();
        assertThat(rc.getClientId()).isEqualTo("busa-finance");
        // TenantResolutionFilter a deja resolu ce client plus haut dans la chaine : un
        // second appel au resolveur observerait potentiellement un etat different.
        verifyNoInteractions(resolvedOAuthClientResolver);
    }

    @Test
    void given_a_context_client_with_a_different_client_id_when_find_by_client_id_then_it_fails_closed() {
        // Un contexte pose mais portant un autre client_id est une divergence, pas une
        // absence : retomber sur le resolveur romprait la garantie d'une seule resolution
        // par requete en resolvant B en base pendant qu'un contexte pour A est actif.
        ResolvedOAuthClientContextHolder.set(aClient("other-client").build());

        RegisteredClient rc = repository.findByClientId("busa-finance");

        assertThat(rc).isNull();
        verifyNoInteractions(resolvedOAuthClientResolver);
    }

    @Test
    void given_no_resolved_client_in_context_when_find_by_client_id_then_it_resolves_directly() {
        // Appel hors de la requete filtree : findByClientId reste utilisable seul.
        when(resolvedOAuthClientResolver.resolve("busa-finance"))
                .thenReturn(Optional.of(aClient("busa-finance").build()));

        RegisteredClient rc = repository.findByClientId("busa-finance");

        assertThat(rc).isNotNull();
        verify(resolvedOAuthClientResolver).resolve("busa-finance");
    }

    // ---------- findByClientId : politique OAuth complete ----------

    @Test
    void given_a_resolved_client_requiring_consent_then_client_settings_reflect_it() {
        when(resolvedOAuthClientResolver.resolve("busa-finance")).thenReturn(Optional.of(
                aClient("busa-finance").requireConsent(true).build()));

        RegisteredClient rc = repository.findByClientId("busa-finance");

        assertThat(rc.getClientSettings().isRequireAuthorizationConsent()).isTrue();
    }

    @Test
    void given_resolved_client_ttls_then_token_settings_reflect_them() {
        when(resolvedOAuthClientResolver.resolve("busa-finance")).thenReturn(Optional.of(
                aClient("busa-finance")
                        .accessTokenTtl(Duration.ofMinutes(5))
                        .refreshTokenTtl(Duration.ofDays(30))
                        .idTokenTtl(Duration.ofMinutes(10))
                        .build()));

        RegisteredClient rc = repository.findByClientId("busa-finance");

        assertThat(rc.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(5));
        assertThat(rc.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(30));
        assertThat((Long) rc.getTokenSettings().getSetting(TakiboTokenClaims.ID_TOKEN_TTL_SECONDS))
                .isEqualTo(600L);
    }

    @Test
    void given_no_consent_or_ttl_set_then_spring_authorization_server_defaults_are_kept() {
        when(resolvedOAuthClientResolver.resolve("busa-finance"))
                .thenReturn(Optional.of(aClient("busa-finance").build()));

        RegisteredClient rc = repository.findByClientId("busa-finance");

        assertThat(rc.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(rc.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(org.springframework.security.oauth2.server.authorization.settings.TokenSettings
                        .builder().build().getAccessTokenTimeToLive());
    }

    @Test
    void given_resolved_client_when_find_by_client_id_then_maps_to_registered_client_with_scope_bound_settings() {
        when(resolvedOAuthClientResolver.resolve("busa-finance"))
                .thenReturn(Optional.of(aClient("busa-finance").build()));

        RegisteredClient rc = repository.findByClientId("busa-finance");

        assertThat(rc).isNotNull();
        assertThat(rc.getId()).isEqualTo(ID.toString());
        assertThat(rc.getClientId()).isEqualTo("busa-finance");
        assertThat(rc.getClientSecret()).isEqualTo("hash");
        assertThat(rc.getScopes()).contains("api.read");
        assertThat(rc.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat((String) rc.getClientSettings().getSetting("takibo_scope_level")).isEqualTo("SPACE");
        assertThat((String) rc.getClientSettings().getSetting("takibo_tenant_source")).isEqualTo("oauth2_client");
        assertThat((String) rc.getClientSettings().getSetting("org_id")).isEqualTo(ORG.toString());
        assertThat((String) rc.getClientSettings().getSetting("space_id")).isEqualTo(SPACE.toString());
    }

    @Test
    void given_public_resolved_client_when_find_by_client_id_then_registered_client_has_no_secret() {
        when(resolvedOAuthClientResolver.resolve("public-client")).thenReturn(Optional.of(
                aClient("public-client").publicClient().scopes("openid").build()));

        RegisteredClient rc = repository.findByClientId("public-client");

        assertThat(rc).isNotNull();
        assertThat(rc.getClientSecret()).isNull();
        assertThat(rc.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(rc.getScopes()).contains("openid");
    }

    @Test
    void given_private_key_jwt_resolved_client_when_find_then_maps_verification_key_settings() {
        when(resolvedOAuthClientResolver.resolve("signed-client")).thenReturn(Optional.of(
                aClient("signed-client").privateKeyJwt()
                        .jwksUri("https://keys.example/jwks.json")
                        .jwksJson("{\"keys\":[]}")
                        .idTokenSignedAlg("RS256")
                        .build()));

        RegisteredClient rc = repository.findByClientId("signed-client");

        assertThat(rc).isNotNull();
        assertThat(rc.getClientSettings().getJwkSetUrl())
                .isEqualTo("https://keys.example/jwks.json");
        assertThat((String) rc.getClientSettings().getSetting(
                TakiboJwtClientAssertionDecoderFactory.JWK_SET_JSON_SETTING))
                .isEqualTo("{\"keys\":[]}");
        assertThat(rc.getClientSettings().getTokenEndpointAuthenticationSigningAlgorithm())
                .isEqualTo(SignatureAlgorithm.RS256);
        // Confidentiel via private_key_jwt : n'exige aucun secret sans devenir PUBLIC.
        assertThat(rc.getClientSecret()).isNull();
    }

    @Test
    void given_legacy_unsupported_algorithm_when_find_then_does_not_expose_it_to_spring_security() {
        when(resolvedOAuthClientResolver.resolve("legacy-client")).thenReturn(Optional.of(
                aClient("legacy-client").privateKeyJwt().idTokenSignedAlg("EdDSA").build()));

        RegisteredClient rc = repository.findByClientId("legacy-client");

        assertThat(rc).isNotNull();
        assertThat(rc.getClientSettings().getTokenEndpointAuthenticationSigningAlgorithm()).isNull();
    }

    @Test
    void given_authorization_code_resolved_client_with_redirect_uris_when_find_then_maps_redirect_uris() {
        when(resolvedOAuthClientResolver.resolve("web-app")).thenReturn(Optional.of(
                aClient("web-app")
                        .grantTypes("authorization_code")
                        .scopes("openid")
                        .redirectUris("https://app.takibo.io/callback")
                        .build()));

        RegisteredClient rc = repository.findByClientId("web-app");

        assertThat(rc).isNotNull();
        assertThat(rc.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(rc.getRedirectUris()).contains("https://app.takibo.io/callback");
    }

    @Test
    void given_the_resolver_returns_empty_when_find_by_client_id_then_returns_null() {
        // Client inconnu, sans grant type, secret expire... toutes les raisons de refus se
        // resument a un Optional.empty() du resolveur : ce depot n'en connait plus le detail.
        when(resolvedOAuthClientResolver.resolve("nope")).thenReturn(Optional.empty());

        assertThat(repository.findByClientId("nope")).isNull();
    }

    // ---------- findById : chemin direct, hors du perimetre du resolveur ----------

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
    void given_registered_client_when_save_then_throws_read_only_exception() {
        RegisteredClient client = RegisteredClient.withId(ID.toString())
                .clientId("busa-finance")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();

        assertThatThrownBy(() -> repository.save(client))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("management-service");
    }

    // ---------- Fixtures : ResolvedOAuthClient (findByClientId) ----------

    private static Builder aClient(String clientId) {
        return new Builder(clientId);
    }

    private static final class Builder {
        private final String clientId;
        private ClientType clientType = ClientType.CONFIDENTIAL;
        private boolean requireClientSecret = true;
        private String clientSecretHash = "hash";
        private String tokenEndpointAuthMethod = "client_secret_basic";
        private boolean requireConsent = false;
        private String jwksUri;
        private String jwksJson;
        private String idTokenSignedAlg;
        private java.time.Duration accessTokenTtl;
        private java.time.Duration refreshTokenTtl;
        private java.time.Duration idTokenTtl;
        private Set<String> scopes = Set.of("api.read");
        private Set<String> grantTypes = Set.of("client_credentials");
        private Set<String> redirectUris = Set.of();

        Builder(String clientId) {
            this.clientId = clientId;
        }

        Builder publicClient() {
            clientType = ClientType.PUBLIC;
            requireClientSecret = false;
            clientSecretHash = null;
            tokenEndpointAuthMethod = "none";
            return this;
        }

        Builder privateKeyJwt() {
            requireClientSecret = false;
            clientSecretHash = null;
            tokenEndpointAuthMethod = "private_key_jwt";
            return this;
        }

        Builder jwksUri(String v) { jwksUri = v; return this; }
        Builder jwksJson(String v) { jwksJson = v; return this; }
        Builder idTokenSignedAlg(String v) { idTokenSignedAlg = v; return this; }
        Builder requireConsent(boolean v) { requireConsent = v; return this; }
        Builder accessTokenTtl(java.time.Duration v) { accessTokenTtl = v; return this; }
        Builder refreshTokenTtl(java.time.Duration v) { refreshTokenTtl = v; return this; }
        Builder idTokenTtl(java.time.Duration v) { idTokenTtl = v; return this; }
        Builder scopes(String... v) { scopes = Set.of(v); return this; }
        Builder grantTypes(String... v) { grantTypes = Set.of(v); return this; }
        Builder redirectUris(String... v) { redirectUris = Set.of(v); return this; }

        ResolvedOAuthClient build() {
            return new ResolvedOAuthClient(
                    ID.toString(), clientId, ClientPlan.SPACE, ORG, SPACE,
                    clientType, false, requireConsent, requireClientSecret, clientSecretHash,
                    tokenEndpointAuthMethod, jwksUri, jwksJson, idTokenSignedAlg,
                    accessTokenTtl, refreshTokenTtl, idTokenTtl, scopes, grantTypes, redirectUris, Set.of());
        }
    }

    // ---------- Fixtures : OAuth2ClientLookupEntity (findById) ----------

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
}
