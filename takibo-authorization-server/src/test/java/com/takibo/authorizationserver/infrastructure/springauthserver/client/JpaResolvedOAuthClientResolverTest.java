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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaResolvedOAuthClientResolverTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG = UUID.fromString("674b889c-4d4e-47bd-bdf6-972dc84f1b49");
    private static final UUID SPACE = UUID.fromString("8932f9bc-0af0-4c64-94c8-abb0150c348b");
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    @Mock private OAuth2ClientLookupRepository clients;
    @Mock private OAuth2ClientGrantTypeRepository grantTypes;
    @Mock private OAuth2ClientScopeRepository scopes;
    @Mock private OAuth2ClientRedirectUriRepository redirectUris;
    @Mock private OAuth2ClientPostLogoutRedirectUriRepository postLogoutRedirectUris;
    @Mock private Clock clock;

    @InjectMocks private JpaResolvedOAuthClientResolver resolver;

    @Test
    void given_a_db_client_then_it_resolves_as_space() {
        OAuth2ClientLookupEntity entity = clientEntity(
                OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL, true, "$2a$12$hashvalue");
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
        OAuth2ClientLookupEntity entity =
                clientEntity(OAuth2ClientLookupEntity.ClientType.PUBLIC, false, null);
        when(clients.findByClientId("spa-client")).thenReturn(Optional.of(entity));
        givenGrantTypes("authorization_code");

        ResolvedOAuthClient client = resolver.resolve("spa-client").orElseThrow();

        assertThat(client.clientType()).isEqualTo(ClientType.PUBLIC);
        assertThat(client.requireClientSecret()).isFalse();
    }

    @Test
    void given_a_confidential_client_without_a_required_secret_then_it_still_resolves_as_confidential() {
        // Le type du client vient de client_type, jamais derive de require_client_secret : un
        // client confidentiel authentifie par private_key_jwt n'exige pas de secret, mais
        // n'en reste pas moins confidentiel — lui imposer PKCE serait un faux positif.
        OAuth2ClientLookupEntity entity =
                clientEntity(OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL, false, null);
        when(entity.getTokenEndpointAuthMethod()).thenReturn("private_key_jwt");
        when(clients.findByClientId("jwt-client")).thenReturn(Optional.of(entity));
        givenGrantTypes("client_credentials");

        ResolvedOAuthClient client = resolver.resolve("jwt-client").orElseThrow();

        assertThat(client.clientType()).isEqualTo(ClientType.CONFIDENTIAL);
        assertThat(client.requireClientSecret()).isFalse();
        assertThat(client.pkceRequired()).isFalse();
    }

    @Test
    void given_a_client_requiring_consent_then_it_resolves_with_consent_required() {
        OAuth2ClientLookupEntity entity =
                clientEntity(OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL, true, "hash");
        when(entity.getRequireConsent()).thenReturn(true);
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));
        givenGrantTypes("authorization_code");

        ResolvedOAuthClient client = resolver.resolve("busa-finance").orElseThrow();

        assertThat(client.requireConsent()).isTrue();
    }

    @Test
    void given_ttl_columns_set_then_they_map_to_durations() {
        OAuth2ClientLookupEntity entity =
                clientEntity(OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL, true, "hash");
        when(entity.getAccessTokenTtlSeconds()).thenReturn(3600);
        when(entity.getRefreshTokenTtlSeconds()).thenReturn(86400);
        when(entity.getIdTokenTtlSeconds()).thenReturn(1800);
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));
        givenGrantTypes("client_credentials");

        ResolvedOAuthClient client = resolver.resolve("busa-finance").orElseThrow();

        assertThat(client.accessTokenTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(client.refreshTokenTtl()).isEqualTo(Duration.ofDays(1));
        assertThat(client.idTokenTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void given_no_ttl_column_set_then_the_defaults_are_left_to_spring_authorization_server() {
        OAuth2ClientLookupEntity entity =
                clientEntity(OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL, true, "hash");
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));
        givenGrantTypes("client_credentials");

        ResolvedOAuthClient client = resolver.resolve("busa-finance").orElseThrow();

        assertThat(client.accessTokenTtl()).isNull();
        assertThat(client.refreshTokenTtl()).isNull();
        assertThat(client.idTokenTtl()).isNull();
    }

    @Test
    void given_the_resolve_method_then_it_is_wrapped_in_a_repeatable_read_read_only_transaction()
            throws NoSuchMethodException {
        // Les cinq lectures (client, grants, scopes, URI, URI de post-deconnexion) portent
        // sur des tables separees ; sans REPEATABLE READ, une modification concurrente entre
        // deux d'entre elles produirait un instantane mele. La garantie elle-meme, sur
        // PostgreSQL reel, est fixee par un test d'integration separe.
        var resolveMethod = JpaResolvedOAuthClientResolver.class.getMethod("resolve", String.class);
        var transactional = resolveMethod.getAnnotation(
                org.springframework.transaction.annotation.Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.isolation())
                .isEqualTo(org.springframework.transaction.annotation.Isolation.REPEATABLE_READ);
        assertThat(transactional.readOnly()).isTrue();
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
        OAuth2ClientLookupEntity entity =
                clientEntity(OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL, true, null);
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));
        givenGrantTypes("client_credentials");

        assertThat(resolver.resolve("busa-finance")).isEmpty();
    }

    @Test
    void given_a_client_secret_expired_in_the_past_then_it_is_treated_as_not_found() {
        // Entite minimale : la resolution s'arrete a l'echeance du secret, avant tout autre
        // champ, le reste ne serait jamais consulte.
        OAuth2ClientLookupEntity entity = mock(OAuth2ClientLookupEntity.class);
        when(entity.getId()).thenReturn(ID);
        when(entity.getClientSecretExpiresAt())
                .thenReturn(NOW.minusSeconds(1).atOffset(ZoneOffset.UTC));
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));
        when(clock.instant()).thenReturn(NOW);
        givenGrantTypes("client_credentials");

        assertThat(resolver.resolve("busa-finance")).isEmpty();
    }

    @Test
    void given_a_client_secret_expiring_in_the_future_then_it_still_resolves() {
        OAuth2ClientLookupEntity entity =
                clientEntity(OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL, true, "hash");
        when(entity.getClientSecretExpiresAt())
                .thenReturn(NOW.plusSeconds(1).atOffset(ZoneOffset.UTC));
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));
        when(clock.instant()).thenReturn(NOW);
        givenGrantTypes("client_credentials");

        assertThat(resolver.resolve("busa-finance")).isPresent();
    }

    @Test
    void given_no_client_secret_expiry_then_the_clock_is_never_consulted() {
        // Une borne absente ne doit jamais requerir l'heure courante.
        OAuth2ClientLookupEntity entity =
                clientEntity(OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL, true, "hash");
        when(clients.findByClientId("busa-finance")).thenReturn(Optional.of(entity));
        givenGrantTypes("client_credentials");

        assertThat(resolver.resolve("busa-finance")).isPresent();
        verifyNoInteractions(clock);
    }

    private OAuth2ClientLookupEntity clientEntity(
            OAuth2ClientLookupEntity.ClientType clientType, boolean requireClientSecret, String secretHash) {
        OAuth2ClientLookupEntity entity = mock(OAuth2ClientLookupEntity.class);
        when(entity.getId()).thenReturn(ID);
        when(entity.getClientId()).thenReturn(requireClientSecret ? "busa-finance" : "spa-client");
        when(entity.getOrgId()).thenReturn(ORG);
        when(entity.getSpaceId()).thenReturn(SPACE);
        when(entity.getClientType()).thenReturn(clientType);
        when(entity.getTokenEndpointAuthMethod())
                .thenReturn(requireClientSecret ? "client_secret_basic" : "none");
        when(entity.getRequireClientSecret()).thenReturn(requireClientSecret);
        when(entity.getClientSecretHash()).thenReturn(secretHash);
        // Mockito repond 0, jamais null, a un getter Integer non stubbe — contrairement a
        // Hibernate, qui mappe fidelement une colonne SQL NULL. Sans ceci, Duration.ZERO
        // ferait echouer la validation "TTL strictement positif" pour tous ces tests.
        when(entity.getAccessTokenTtlSeconds()).thenReturn(null);
        when(entity.getRefreshTokenTtlSeconds()).thenReturn(null);
        when(entity.getIdTokenTtlSeconds()).thenReturn(null);
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
