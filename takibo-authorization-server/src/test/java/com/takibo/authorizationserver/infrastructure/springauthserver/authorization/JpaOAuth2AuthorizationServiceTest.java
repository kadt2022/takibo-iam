package com.takibo.authorizationserver.infrastructure.springauthserver.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.authorizationserver.domain.authorization.TokenHash;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2AuthorizationEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2AuthorizationRepository;
import com.takibo.authorizationserver.infrastructure.keys.AesGcmSecretCipher;
import com.takibo.authorizationserver.infrastructure.keys.SecretCipherKey;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.TakiboTokenClaims;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Round-trip {@code save}/{@code findById}/{@code findByToken} de {@link JpaOAuth2AuthorizationService}
 * (TAS-GRANTS-02), avec le vrai {@link SecretCipher} et le vrai {@code ObjectMapper} de
 * {@link OAuth2AuthorizationJacksonConfig} — seul {@link OAuth2AuthorizationRepository} est
 * simulé, en mémoire, pour prouver le mapping sans dépendre de PostgreSQL.
 */
class JpaOAuth2AuthorizationServiceTest {

    private static final UUID ORG_ID = UUID.fromString("674b889c-4d4e-47bd-bdf6-972dc84f1b49");
    private static final UUID SPACE_ID = UUID.fromString("8932f9bc-0af0-4c64-94c8-abb0150c348b");

    private final OAuth2AuthorizationRepository authorizations = mock(OAuth2AuthorizationRepository.class);
    private final RegisteredClientRepository registeredClientRepository = mock(RegisteredClientRepository.class);
    private final SecretCipher secretCipher = new AesGcmSecretCipher(aKey());
    private final ObjectMapper objectMapper = new OAuth2AuthorizationJacksonConfig().oauth2AuthorizationObjectMapper();

    private final JpaOAuth2AuthorizationService service = new JpaOAuth2AuthorizationService(
            authorizations, registeredClientRepository, secretCipher, objectMapper);

    // ---------- Round-trip ----------

    @Test
    void given_a_client_credentials_authorization_when_saved_and_reloaded_then_it_round_trips() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(client.getId())).thenReturn(client);
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(3600);

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName("busa-finance")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizedScopes(Set.of("api.read"))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "the-access-token",
                        issuedAt, expiresAt, Set.of("api.read")))
                .build();

        OAuth2AuthorizationEntity entity = save(authorization);
        when(authorizations.findById(UUID.fromString(authorization.getId())))
                .thenReturn(Optional.of(entity));

        OAuth2Authorization reloaded = service.findById(authorization.getId());

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getId()).isEqualTo(authorization.getId());
        assertThat(reloaded.getPrincipalName()).isEqualTo("busa-finance");
        assertThat(reloaded.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(reloaded.getAuthorizedScopes()).containsExactly("api.read");
        OAuth2Authorization.Token<OAuth2AccessToken> reloadedToken =
                reloaded.getToken(OAuth2AccessToken.class);
        assertThat(reloadedToken).isNotNull();
        assertThat(reloadedToken.getToken().getTokenValue()).isEqualTo("the-access-token");
        assertThat(reloadedToken.getToken().getIssuedAt()).isEqualTo(issuedAt);
        assertThat(reloadedToken.getToken().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(reloadedToken.getToken().getScopes()).containsExactly("api.read");
    }

    @Test
    void given_a_client_credentials_authorization_then_the_entity_is_marked_client_app_without_an_account() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(client.getId())).thenReturn(client);
        OAuth2Authorization authorization = clientCredentialsAuthorization(client);

        OAuth2AuthorizationEntity entity = save(authorization);

        assertThat(entity.getSubjectType()).isEqualTo("CLIENT_APP");
        assertThat(entity.getPrincipalAccountId()).isNull();
        assertThat(entity.getPrincipalName()).isEqualTo(authorization.getPrincipalName());
    }

    @Test
    void given_an_authorization_code_grant_then_the_entity_is_marked_human() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(client.getId())).thenReturn(client);
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName("user@takibo.test")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();

        OAuth2AuthorizationEntity entity = save(authorization);

        assertThat(entity.getSubjectType()).isEqualTo("HUMAN");
        assertThat(entity.getPrincipalAccountId()).isNull();
    }

    @Test
    void given_a_space_client_then_org_and_space_are_taken_from_its_client_settings() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(client.getId())).thenReturn(client);
        OAuth2Authorization authorization = clientCredentialsAuthorization(client);

        OAuth2AuthorizationEntity entity = save(authorization);

        assertThat(entity.getOrgId()).isEqualTo(ORG_ID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_ID);
    }

    @Test
    void given_a_platform_client_then_org_and_space_are_null() {
        RegisteredClient client = platformClient();
        when(registeredClientRepository.findById(client.getId())).thenReturn(client);
        OAuth2Authorization authorization = clientCredentialsAuthorization(client);

        OAuth2AuthorizationEntity entity = save(authorization);

        assertThat(entity.getOrgId()).isNull();
        assertThat(entity.getSpaceId()).isNull();
    }

    @Test
    void given_every_token_type_then_each_value_is_encrypted_and_hashed_and_all_round_trip() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(client.getId())).thenReturn(client);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName("user@takibo.test")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new OAuth2AuthorizationCode("the-code", now, now.plusSeconds(300)))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "the-access-token",
                        now, now.plusSeconds(3600)))
                .token(new OidcIdToken("the-id-token", now, now.plusSeconds(3600), Map.of("sub", "user")),
                        metadata -> metadata.put(
                                OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                                // new HashMap, pas Map.of(...) : SecurityJackson2Modules
                                // n'autorise pas ImmutableCollections$Map1 en deserialisation
                                // (allowlist de types surs) — HashMap/LinkedHashMap, comme en
                                // produit reellement Spring Authorization Server, le sont.
                                new java.util.HashMap<>(Map.of("sub", "user"))))
                .token(new OAuth2RefreshToken("the-refresh-token", now))
                .token(new OAuth2UserCode("THE-USER-CODE", now, now.plusSeconds(600)))
                .token(new OAuth2DeviceCode("the-device-code", now, now.plusSeconds(600)))
                .build();

        OAuth2AuthorizationEntity entity = save(authorization);

        // Chiffre autoportant, jamais le clair, dans chacune des six colonnes _value.
        assertThat(entity.getAuthorizationCodeValue()).isNotEqualTo("the-code").contains("$");
        assertThat(entity.getAccessTokenValue()).isNotEqualTo("the-access-token").contains("$");
        assertThat(entity.getOidcIdTokenValue()).isNotEqualTo("the-id-token").contains("$");
        assertThat(entity.getRefreshTokenValue()).isNotEqualTo("the-refresh-token").contains("$");
        assertThat(entity.getUserCodeValue()).isNotEqualTo("THE-USER-CODE").contains("$");
        assertThat(entity.getDeviceCodeValue()).isNotEqualTo("the-device-code").contains("$");
        assertThat(entity.getAuthorizationCodeHash()).matches("^[a-f0-9]{64}$");
        assertThat(entity.getAccessTokenHash()).matches("^[a-f0-9]{64}$");

        when(authorizations.findById(UUID.fromString(authorization.getId())))
                .thenReturn(Optional.of(entity));
        OAuth2Authorization reloaded = service.findById(authorization.getId());

        assertThat(reloaded.getToken(OAuth2AuthorizationCode.class).getToken().getTokenValue())
                .isEqualTo("the-code");
        assertThat(reloaded.getToken(OAuth2AccessToken.class).getToken().getTokenValue())
                .isEqualTo("the-access-token");
        OidcIdToken idToken = reloaded.getToken(OidcIdToken.class).getToken();
        assertThat(idToken.getTokenValue()).isEqualTo("the-id-token");
        assertThat(idToken.getClaims()).containsEntry("sub", "user");
        assertThat(reloaded.getToken(OAuth2RefreshToken.class).getToken().getTokenValue())
                .isEqualTo("the-refresh-token");
        assertThat(reloaded.getToken(OAuth2UserCode.class).getToken().getTokenValue())
                .isEqualTo("THE-USER-CODE");
        assertThat(reloaded.getToken(OAuth2DeviceCode.class).getToken().getTokenValue())
                .isEqualTo("the-device-code");
    }

    @Test
    void given_attributes_and_state_then_they_round_trip() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(client.getId())).thenReturn(client);
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName("user@takibo.test")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute("custom-attribute", "custom-value")
                .attribute(OAuth2ParameterNames.STATE, "the-state-value")
                .build();

        OAuth2AuthorizationEntity entity = save(authorization);
        assertThat(entity.getState()).isEqualTo("the-state-value");

        when(authorizations.findById(UUID.fromString(authorization.getId())))
                .thenReturn(Optional.of(entity));
        OAuth2Authorization reloaded = service.findById(authorization.getId());

        assertThat(reloaded.<String>getAttribute("custom-attribute")).isEqualTo("custom-value");
        assertThat(reloaded.<String>getAttribute(OAuth2ParameterNames.STATE)).isEqualTo("the-state-value");
    }

    @Test
    void given_a_device_code_authorization_when_found_invalidated_and_resaved_then_the_value_and_hash_survive_without_rehashing() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(client.getId())).thenReturn(client);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName("user@takibo.test")
                .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
                .token(new OAuth2DeviceCode("the-device-code", now, now.plusSeconds(600)))
                .token(new OAuth2UserCode("THE-USER-CODE", now, now.plusSeconds(600)))
                .build();

        service.save(authorization);
        ArgumentCaptor<OAuth2AuthorizationEntity> firstSave = ArgumentCaptor.forClass(OAuth2AuthorizationEntity.class);
        verify(authorizations).save(firstSave.capture());
        OAuth2AuthorizationEntity savedEntity = firstSave.getValue();

        // Retrouve par le device code, comme le ferait la validation periodique du client.
        when(authorizations.findByDeviceCodeHash(savedEntity.getDeviceCodeHash()))
                .thenReturn(Optional.of(savedEntity));
        OAuth2Authorization reloaded = service.findByToken(
                "the-device-code", new OAuth2TokenType(OAuth2ParameterNames.DEVICE_CODE));
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getToken(OAuth2DeviceCode.class).getToken().getTokenValue())
                .isEqualTo("the-device-code");

        // Invalide et resauvegarde -- exactement le cycle qu'un flux device declenche a
        // l'approbation ou a l'expiration du device code.
        OAuth2Authorization invalidated = OAuth2Authorization.from(reloaded)
                .invalidate(reloaded.getToken(OAuth2DeviceCode.class).getToken())
                .build();
        service.save(invalidated);

        ArgumentCaptor<OAuth2AuthorizationEntity> secondSave = ArgumentCaptor.forClass(OAuth2AuthorizationEntity.class);
        verify(authorizations, times(2)).save(secondSave.capture());
        OAuth2AuthorizationEntity resavedEntity = secondSave.getAllValues().get(1);

        // Le hash ne bouge pas : il reste celui du device code d'origine, jamais un hash du
        // hash ou d'un chiffre intermediaire recalcule a partir d'une valeur deja scellee.
        assertThat(resavedEntity.getDeviceCodeHash()).isEqualTo(savedEntity.getDeviceCodeHash());

        when(authorizations.findByDeviceCodeHash(resavedEntity.getDeviceCodeHash()))
                .thenReturn(Optional.of(resavedEntity));
        OAuth2Authorization reloadedAfterInvalidation = service.findByToken(
                "the-device-code", new OAuth2TokenType(OAuth2ParameterNames.DEVICE_CODE));

        assertThat(reloadedAfterInvalidation.getToken(OAuth2DeviceCode.class).getToken().getTokenValue())
                .isEqualTo("the-device-code");
        assertThat(reloadedAfterInvalidation.getToken(OAuth2DeviceCode.class).isInvalidated()).isTrue();
        // Le user code, non touche par l'invalidation du device code, reste lui aussi intact.
        assertThat(reloadedAfterInvalidation.getToken(OAuth2UserCode.class).getToken().getTokenValue())
                .isEqualTo("THE-USER-CODE");
    }

    // ---------- findByToken : repartition par type ----------

    @Test
    void given_a_state_lookup_then_it_searches_by_state_directly_not_by_hash() {
        when(authorizations.findByState("the-state-value")).thenReturn(Optional.empty());

        service.findByToken("the-state-value", new OAuth2TokenType(OAuth2ParameterNames.STATE));

        verify(authorizations).findByState("the-state-value");
    }

    @Test
    void given_an_access_token_lookup_then_it_searches_by_the_tokens_hash() {
        when(authorizations.findByAccessTokenHash(any())).thenReturn(Optional.empty());

        service.findByToken("the-access-token", OAuth2TokenType.ACCESS_TOKEN);

        verify(authorizations).findByAccessTokenHash(
                TokenHash.sha256Hex("the-access-token"));
    }

    @Test
    void given_an_id_token_lookup_then_it_searches_by_the_id_tokens_hash() {
        when(authorizations.findByOidcIdTokenHash(any())).thenReturn(Optional.empty());

        service.findByToken("the-id-token", new OAuth2TokenType(OidcParameterNames.ID_TOKEN));

        verify(authorizations).findByOidcIdTokenHash(any());
    }

    @Test
    void given_a_null_token_type_then_state_is_checked_before_any_hash_lookup() {
        when(authorizations.findByState("token-or-state")).thenReturn(Optional.empty());
        when(authorizations.findByAuthorizationCodeHash(any())).thenReturn(Optional.empty());
        when(authorizations.findByAccessTokenHash(any())).thenReturn(Optional.empty());
        when(authorizations.findByOidcIdTokenHash(any())).thenReturn(Optional.empty());
        when(authorizations.findByRefreshTokenHash(any())).thenReturn(Optional.empty());
        when(authorizations.findByUserCodeHash(any())).thenReturn(Optional.empty());
        when(authorizations.findByDeviceCodeHash(any())).thenReturn(Optional.empty());

        service.findByToken("token-or-state", null);

        verify(authorizations).findByState("token-or-state");
    }

    // ---------- Fail-closed ----------

    @Test
    void given_an_unresolvable_client_when_saving_then_it_fails_closed() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(client.getId())).thenReturn(null);
        OAuth2Authorization authorization = clientCredentialsAuthorization(client);

        assertThatThrownBy(() -> service.save(authorization))
                .isInstanceOf(DataRetrievalFailureException.class);
    }

    @Test
    void given_an_unresolvable_client_when_reloading_then_it_fails_closed() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(client.getId())).thenReturn(client);
        OAuth2Authorization authorization = clientCredentialsAuthorization(client);
        OAuth2AuthorizationEntity entity = save(authorization);
        when(registeredClientRepository.findById(client.getId())).thenReturn(null);
        when(authorizations.findById(UUID.fromString(authorization.getId())))
                .thenReturn(Optional.of(entity));
        String authorizationId = authorization.getId();

        assertThatThrownBy(() -> service.findById(authorizationId))
                .isInstanceOf(DataRetrievalFailureException.class);
    }

    // ---------- Fixtures ----------

    private OAuth2AuthorizationEntity save(OAuth2Authorization authorization) {
        service.save(authorization);
        ArgumentCaptor<OAuth2AuthorizationEntity> captor =
                ArgumentCaptor.forClass(OAuth2AuthorizationEntity.class);
        verify(authorizations).save(captor.capture());
        return captor.getValue();
    }

    private static OAuth2Authorization clientCredentialsAuthorization(RegisteredClient client) {
        return OAuth2Authorization.withRegisteredClient(client)
                .principalName(client.getClientId())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizedScopes(Set.of("api.read"))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "the-access-token",
                        Instant.now(), Instant.now().plusSeconds(3600)))
                .build();
    }

    private static RegisteredClient spaceClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("busa-finance")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app.takibo.io/callback")
                .scope("api.read")
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboTokenClaims.ORG_ID, ORG_ID.toString())
                        .setting(TakiboTokenClaims.SPACE_ID, SPACE_ID.toString())
                        .build())
                .build();
    }

    private static RegisteredClient platformClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("postman-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("api.read")
                .build();
    }

    private static SecretCipherKey aKey() {
        byte[] material = new byte[32];
        for (int i = 0; i < material.length; i++) {
            material[i] = (byte) i;
        }
        return new SecretCipherKey("test-key", material);
    }
}
