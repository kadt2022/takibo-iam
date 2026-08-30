package com.takibo.authorizationserver.infrastructure.springauthserver.authorization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.authorizationserver.domain.authorization.EncryptedTokenValue;
import com.takibo.authorizationserver.domain.authorization.TokenHash;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import com.takibo.authorizationserver.domain.keys.port.UserCodeHmac;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2AuthorizationEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2AuthorizationRepository;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.TakiboTokenClaims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.takibo.authorizationserver.infrastructure.springauthserver.authorization.OAuth2AuthorizationJacksonConfig.OAUTH2_AUTHORIZATION_OBJECT_MAPPER;

/**
 * {@link OAuth2AuthorizationService} persistant : autorisations, codes et tokens survivent au
 * redémarrage de TAS (TAS-GRANTS-02).
 * <p>
 * Mappe directement {@link OAuth2Authorization} de Spring Authorization Server vers
 * {@link OAuth2AuthorizationEntity}, sans DTO intermédiaire — même principe que
 * {@code JdbcOAuth2AuthorizationService}, la référence que fournit Spring lui-même, dont cette
 * classe reprend délibérément la structure ({@code findByToken} par type, mapping token par
 * token) pour rester reconnaissable face à elle.
 * <p>
 * Trois différences assumées avec cette référence :
 * <ul>
 *   <li>chaque valeur de token est chiffrée ({@link EncryptedTokenValue}, TAS-GRANTS-02A),
 *       jamais stockée en clair, et recherchée par son hash ({@link TokenHash}), jamais par
 *       égalité sur la valeur ;</li>
 *   <li>{@code org_id}/{@code space_id} sont dérivés du {@link RegisteredClient} résolu, pas
 *       lus depuis l'autorisation elle-même, qui ne les porte pas ;</li>
 *   <li>{@code subject_type} distingue CLIENT_APP (le principal d'un {@code client_credentials}
 *       est le client lui-même) de HUMAN. Sans flux de connexion humaine encore branché
 *       (TAS-GRANTS-03), {@code principal_account_id} reste NULL dans les deux cas — le
 *       résoudre à partir du seul {@code principal_name} exigerait un port de résolution de
 *       compte qui n'existe pas encore et n'entre pas dans le périmètre de ce récit.</li>
 * </ul>
 */
@Service
@Slf4j
public class JpaOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationRepository authorizations;
    private final RegisteredClientRepository registeredClientRepository;
    private final SecretCipher secretCipher;
    private final UserCodeHmac userCodeHmac;
    private final ObjectMapper objectMapper;

    public JpaOAuth2AuthorizationService(
            OAuth2AuthorizationRepository authorizations,
            RegisteredClientRepository registeredClientRepository,
            SecretCipher secretCipher,
            UserCodeHmac userCodeHmac,
            @Qualifier(OAUTH2_AUTHORIZATION_OBJECT_MAPPER) ObjectMapper objectMapper) {
        this.authorizations = authorizations;
        this.registeredClientRepository = registeredClientRepository;
        this.secretCipher = secretCipher;
        this.userCodeHmac = userCodeHmac;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        authorizations.save(toEntity(authorization));
    }

    @Override
    @Transactional
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        authorizations.deleteById(UUID.fromString(authorization.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        return authorizations.findById(UUID.fromString(id)).map(this::toDomain).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");
        return findEntityByToken(token, tokenType).map(this::toDomain).orElse(null);
    }

    private Optional<OAuth2AuthorizationEntity> findEntityByToken(String token, OAuth2TokenType tokenType) {
        if (tokenType == null) {
            Optional<OAuth2AuthorizationEntity> byState = authorizations.findByState(token);
            if (byState.isPresent()) {
                return byState;
            }
            String hash = TokenHash.sha256Hex(token);
            // user_code seul est hache par un HMAC d'installation, pas TokenHash.sha256Hex --
            // voir UserCodeHmac.
            String userCodeHash = userCodeHmac.hmacHex(token);
            return firstPresent(
                    () -> authorizations.findByAuthorizationCodeHash(hash),
                    () -> authorizations.findByAccessTokenHash(hash),
                    () -> authorizations.findByOidcIdTokenHash(hash),
                    () -> authorizations.findByRefreshTokenHash(hash),
                    () -> authorizations.findByUserCodeHash(userCodeHash),
                    () -> authorizations.findByDeviceCodeHash(hash));
        }
        if (OAuth2ParameterNames.STATE.equals(tokenType.getValue())) {
            return authorizations.findByState(token);
        }
        if (OAuth2ParameterNames.USER_CODE.equals(tokenType.getValue())) {
            return authorizations.findByUserCodeHash(userCodeHmac.hmacHex(token));
        }
        String hash = TokenHash.sha256Hex(token);
        if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
            return authorizations.findByAuthorizationCodeHash(hash);
        } else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            return authorizations.findByAccessTokenHash(hash);
        } else if (OidcParameterNames.ID_TOKEN.equals(tokenType.getValue())) {
            return authorizations.findByOidcIdTokenHash(hash);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            return authorizations.findByRefreshTokenHash(hash);
        } else if (OAuth2ParameterNames.DEVICE_CODE.equals(tokenType.getValue())) {
            return authorizations.findByDeviceCodeHash(hash);
        }
        return Optional.empty();
    }

    @SafeVarargs
    private static Optional<OAuth2AuthorizationEntity> firstPresent(
            Supplier<Optional<OAuth2AuthorizationEntity>>... suppliers) {
        for (Supplier<Optional<OAuth2AuthorizationEntity>> supplier : suppliers) {
            Optional<OAuth2AuthorizationEntity> found = supplier.get();
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    // ---------- OAuth2Authorization -> OAuth2AuthorizationEntity ----------

    private OAuth2AuthorizationEntity toEntity(OAuth2Authorization authorization) {
        RegisteredClient client = requireResolvableClient(authorization.getRegisteredClientId());
        String authorizationId = authorization.getId();

        SealedToken code = seal("authorization_code_value", authorizationId,
                authorization.getToken(OAuth2AuthorizationCode.class));
        SealedToken access = seal("access_token_value", authorizationId,
                authorization.getToken(OAuth2AccessToken.class));
        SealedToken idToken = seal("oidc_id_token_value", authorizationId,
                authorization.getToken(OidcIdToken.class));
        SealedToken refresh = seal("refresh_token_value", authorizationId,
                authorization.getToken(OAuth2RefreshToken.class));
        SealedToken userCode = seal("user_code_value", authorizationId,
                authorization.getToken(OAuth2UserCode.class), userCodeHmac::hmacHex);
        SealedToken deviceCode = seal("device_code_value", authorizationId,
                authorization.getToken(OAuth2DeviceCode.class));

        OAuth2Authorization.Token<OAuth2AccessToken> accessTokenHolder =
                authorization.getToken(OAuth2AccessToken.class);
        String accessTokenType = accessTokenHolder != null
                ? accessTokenHolder.getToken().getTokenType().getValue() : null;
        String accessTokenScopes = accessTokenHolder != null
                ? joinScopes(accessTokenHolder.getToken().getScopes()) : null;

        return OAuth2AuthorizationEntity.builder()
                .id(UUID.fromString(authorizationId))
                .orgId(readUuidSetting(client, TakiboTokenClaims.ORG_ID))
                .spaceId(readUuidSetting(client, TakiboTokenClaims.SPACE_ID))
                .registeredClientId(authorization.getRegisteredClientId())
                .principalAccountId(null)
                .subjectType(subjectTypeOf(authorization.getAuthorizationGrantType()))
                .principalName(authorization.getPrincipalName())
                .authorizationGrantType(authorization.getAuthorizationGrantType().getValue())
                .authorizedScopes(joinScopes(authorization.getAuthorizedScopes()))
                .attributes(writeJson(authorization.getAttributes()))
                .state(authorization.getAttribute(OAuth2ParameterNames.STATE))
                .authorizationCodeValue(code.value())
                .authorizationCodeHash(code.hash())
                .authorizationCodeIssuedAt(code.issuedAt())
                .authorizationCodeExpiresAt(code.expiresAt())
                .authorizationCodeMetadata(code.metadata())
                .accessTokenValue(access.value())
                .accessTokenHash(access.hash())
                .accessTokenIssuedAt(access.issuedAt())
                .accessTokenExpiresAt(access.expiresAt())
                .accessTokenMetadata(access.metadata())
                .accessTokenType(accessTokenType)
                .accessTokenScopes(accessTokenScopes)
                .oidcIdTokenValue(idToken.value())
                .oidcIdTokenHash(idToken.hash())
                .oidcIdTokenIssuedAt(idToken.issuedAt())
                .oidcIdTokenExpiresAt(idToken.expiresAt())
                .oidcIdTokenMetadata(idToken.metadata())
                .refreshTokenValue(refresh.value())
                .refreshTokenHash(refresh.hash())
                .refreshTokenIssuedAt(refresh.issuedAt())
                .refreshTokenExpiresAt(refresh.expiresAt())
                .refreshTokenMetadata(refresh.metadata())
                .userCodeValue(userCode.value())
                .userCodeHash(userCode.hash())
                .userCodeIssuedAt(userCode.issuedAt())
                .userCodeExpiresAt(userCode.expiresAt())
                .userCodeMetadata(userCode.metadata())
                .deviceCodeValue(deviceCode.value())
                .deviceCodeHash(deviceCode.hash())
                .deviceCodeIssuedAt(deviceCode.issuedAt())
                .deviceCodeExpiresAt(deviceCode.expiresAt())
                .deviceCodeMetadata(deviceCode.metadata())
                .build();
    }

    private static String subjectTypeOf(AuthorizationGrantType grantType) {
        return AuthorizationGrantType.CLIENT_CREDENTIALS.equals(grantType) ? "CLIENT_APP" : "HUMAN";
    }

    /** Ce que chiffrer un token produit pour ses quatre colonnes {@code oauth2_authorization}. */
    private record SealedToken(
            String value, String hash, OffsetDateTime issuedAt, OffsetDateTime expiresAt, String metadata) {

        static final SealedToken ABSENT = new SealedToken(null, null, null, null, null);
    }

    private <T extends OAuth2Token> SealedToken seal(
            String column, String authorizationId, OAuth2Authorization.Token<T> token) {
        return seal(column, authorizationId, token, TokenHash::sha256Hex);
    }

    /**
     * @param hasher {@code TokenHash::sha256Hex} pour les cinq colonnes à haute entropie ;
     *               {@code userCodeHmac::hmacHex} pour {@code user_code_value} seul — voir
     *               {@link UserCodeHmac}.
     */
    private <T extends OAuth2Token> SealedToken seal(
            String column, String authorizationId, OAuth2Authorization.Token<T> token,
            UnaryOperator<String> hasher) {
        if (token == null) {
            return SealedToken.ABSENT;
        }
        EncryptedTokenValue sealed = EncryptedTokenValue.seal(
                secretCipher,
                SecretContext.oauth2AuthorizationValue(column, authorizationId),
                token.getToken().getTokenValue(),
                hasher);
        return new SealedToken(
                sealed.encryptedValue(),
                sealed.hash(),
                toOffsetDateTime(token.getToken().getIssuedAt()),
                toOffsetDateTime(token.getToken().getExpiresAt()),
                writeJson(token.getMetadata()));
    }

    // ---------- OAuth2AuthorizationEntity -> OAuth2Authorization ----------

    private OAuth2Authorization toDomain(OAuth2AuthorizationEntity entity) {
        RegisteredClient client = requireResolvableClient(entity.getRegisteredClientId());
        requireMatchingBoundary(entity.getRegisteredClientId(), entity.getOrgId(), entity.getSpaceId(), client);
        String authorizationId = entity.getId().toString();

        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
                .id(authorizationId)
                .principalName(entity.getPrincipalName())
                .authorizationGrantType(new AuthorizationGrantType(entity.getAuthorizationGrantType()))
                .authorizedScopes(splitScopes(entity.getAuthorizedScopes()))
                .attributes(attrs -> attrs.putAll(readJson(entity.getAttributes())));

        if (StringUtils.hasText(entity.getState())) {
            builder.attribute(OAuth2ParameterNames.STATE, entity.getState());
        }

        if (StringUtils.hasText(entity.getAuthorizationCodeValue())) {
            String value = reveal("authorization_code_value", authorizationId,
                    entity.getAuthorizationCodeValue(), entity.getAuthorizationCodeHash());
            OAuth2AuthorizationCode code = new OAuth2AuthorizationCode(value,
                    toInstant(entity.getAuthorizationCodeIssuedAt()),
                    toInstant(entity.getAuthorizationCodeExpiresAt()));
            Map<String, Object> metadata = readJson(entity.getAuthorizationCodeMetadata());
            builder.token(code, m -> m.putAll(metadata));
        }

        if (StringUtils.hasText(entity.getAccessTokenValue())) {
            String value = reveal("access_token_value", authorizationId,
                    entity.getAccessTokenValue(), entity.getAccessTokenHash());
            OAuth2AccessToken.TokenType tokenType = accessTokenTypeOf(entity.getAccessTokenType());
            OAuth2AccessToken accessToken = new OAuth2AccessToken(tokenType, value,
                    toInstant(entity.getAccessTokenIssuedAt()), toInstant(entity.getAccessTokenExpiresAt()),
                    splitScopes(entity.getAccessTokenScopes()));
            Map<String, Object> metadata = readJson(entity.getAccessTokenMetadata());
            builder.token(accessToken, m -> m.putAll(metadata));
        }

        if (StringUtils.hasText(entity.getOidcIdTokenValue())) {
            String value = reveal("oidc_id_token_value", authorizationId,
                    entity.getOidcIdTokenValue(), entity.getOidcIdTokenHash());
            Map<String, Object> metadata = readJson(entity.getOidcIdTokenMetadata());
            @SuppressWarnings("unchecked")
            Map<String, Object> claims =
                    (Map<String, Object>) metadata.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME);
            OidcIdToken idToken = new OidcIdToken(value, toInstant(entity.getOidcIdTokenIssuedAt()),
                    toInstant(entity.getOidcIdTokenExpiresAt()), claims);
            builder.token(idToken, m -> m.putAll(metadata));
        }

        if (StringUtils.hasText(entity.getRefreshTokenValue())) {
            String value = reveal("refresh_token_value", authorizationId,
                    entity.getRefreshTokenValue(), entity.getRefreshTokenHash());
            OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(value,
                    toInstant(entity.getRefreshTokenIssuedAt()), toInstant(entity.getRefreshTokenExpiresAt()));
            Map<String, Object> metadata = readJson(entity.getRefreshTokenMetadata());
            builder.token(refreshToken, m -> m.putAll(metadata));
        }

        if (StringUtils.hasText(entity.getUserCodeValue())) {
            String value = reveal("user_code_value", authorizationId,
                    entity.getUserCodeValue(), entity.getUserCodeHash(), userCodeHmac::hmacHex);
            OAuth2UserCode userCode = new OAuth2UserCode(value, toInstant(entity.getUserCodeIssuedAt()),
                    toInstant(entity.getUserCodeExpiresAt()));
            Map<String, Object> metadata = readJson(entity.getUserCodeMetadata());
            builder.token(userCode, m -> m.putAll(metadata));
        }

        if (StringUtils.hasText(entity.getDeviceCodeValue())) {
            String value = reveal("device_code_value", authorizationId,
                    entity.getDeviceCodeValue(), entity.getDeviceCodeHash());
            OAuth2DeviceCode deviceCode = new OAuth2DeviceCode(value, toInstant(entity.getDeviceCodeIssuedAt()),
                    toInstant(entity.getDeviceCodeExpiresAt()));
            Map<String, Object> metadata = readJson(entity.getDeviceCodeMetadata());
            builder.token(deviceCode, m -> m.putAll(metadata));
        }

        return builder.build();
    }

    private static OAuth2AccessToken.TokenType accessTokenTypeOf(String value) {
        if (OAuth2AccessToken.TokenType.BEARER.getValue().equalsIgnoreCase(value)) {
            return OAuth2AccessToken.TokenType.BEARER;
        }
        if (OAuth2AccessToken.TokenType.DPOP.getValue().equalsIgnoreCase(value)) {
            return OAuth2AccessToken.TokenType.DPOP;
        }
        return null;
    }

    private String reveal(String column, String authorizationId, String encryptedValue, String hash) {
        return reveal(column, authorizationId, encryptedValue, hash, TokenHash::sha256Hex);
    }

    /** @param hasher exactement celle utilisée à l'écriture — voir {@link #seal} correspondant. */
    private String reveal(
            String column, String authorizationId, String encryptedValue, String hash,
            UnaryOperator<String> hasher) {
        EncryptedTokenValue sealed = new EncryptedTokenValue(encryptedValue, hash);
        return sealed.reveal(secretCipher, SecretContext.oauth2AuthorizationValue(column, authorizationId), hasher);
    }

    // ---------- Fixtures partagées ----------

    private RegisteredClient requireResolvableClient(String registeredClientId) {
        RegisteredClient client = registeredClientRepository.findById(registeredClientId);
        if (client == null) {
            // Ecrit noir sur blanc l'exigence de TAS-GRANTS-02 : une autorisation ne peut se
            // reconstruire que pour un client encore resolvable par ResolvedOAuthClientResolver
            // (via TakiboRegisteredClientRepository) -- jamais silencieusement.
            throw new DataRetrievalFailureException(
                    "The RegisteredClient with id '" + registeredClientId + "' was not found");
        }
        return client;
    }

    /**
     * Refuse de reconstruire une autorisation dont la frontière a divergé de celle du client
     * résolu <b>maintenant</b>. Sans ce contrôle, un client déplacé ou recréé sous une autre
     * organisation/space entre l'émission et la relecture ferait rejouer un refresh token
     * sous le nouveau tenant : {@code TakiboOAuth2TokenCustomizer} lit {@code org_id}/
     * {@code space_id} depuis le {@code RegisteredClient} résolu à l'instant présent, jamais
     * depuis l'autorisation elle-même, qui ne les porte pas de façon indépendante. Échoue
     * fermé plutôt que de laisser un token franchir silencieusement une frontière de tenant.
     */
    private static void requireMatchingBoundary(
            String registeredClientId, UUID savedOrgId, UUID savedSpaceId, RegisteredClient client) {
        UUID currentOrgId = readUuidSetting(client, TakiboTokenClaims.ORG_ID);
        UUID currentSpaceId = readUuidSetting(client, TakiboTokenClaims.SPACE_ID);
        if (!Objects.equals(savedOrgId, currentOrgId) || !Objects.equals(savedSpaceId, currentSpaceId)) {
            throw new DataRetrievalFailureException(
                    "The RegisteredClient with id '" + registeredClientId
                            + "' no longer resolves under the org/space this authorization was saved for");
        }
    }

    private static UUID readUuidSetting(RegisteredClient client, String settingName) {
        String value = client.getClientSettings().getSetting(settingName);
        return StringUtils.hasText(value) ? UUID.fromString(value) : null;
    }

    private static String joinScopes(Set<String> scopes) {
        return CollectionUtils.isEmpty(scopes) ? null : StringUtils.collectionToDelimitedString(scopes, ",");
    }

    private static Set<String> splitScopes(String commaDelimited) {
        return StringUtils.hasText(commaDelimited)
                ? StringUtils.commaDelimitedListToSet(commaDelimited)
                : Collections.emptySet();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.toInstant();
    }

    private String writeJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize authorization attributes/metadata", e);
        }
    }

    private Map<String, Object> readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to deserialize authorization attributes/metadata", e);
        }
    }
}
