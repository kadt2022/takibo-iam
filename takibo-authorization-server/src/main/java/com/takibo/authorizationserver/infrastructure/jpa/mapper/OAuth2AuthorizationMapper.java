package com.takibo.authorizationserver.infrastructure.jpa.mapper;

import com.takibo.authorizationserver.domain.authz.model.HashedToken;
import com.takibo.authorizationserver.domain.authz.model.OAuth2Authorization;
import com.takibo.authorizationserver.domain.authz.model.StoredToken;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2AuthorizationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Map;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OAuth2AuthorizationMapper {

    @Mapping(target = "authorizationCode", expression = "java(toHashedToken(entity.getAuthorizationCodeHash(), entity.getAuthorizationCodeIssuedAt(), entity.getAuthorizationCodeExpiresAt(), entity.getAuthorizationCodeMetadata()))")
    @Mapping(target = "accessToken", expression = "java(toStoredToken(entity.getAccessTokenValue(), entity.getAccessTokenHash(), entity.getAccessTokenIssuedAt(), entity.getAccessTokenExpiresAt(), entity.getAccessTokenMetadata(), entity.getAccessTokenType(), entity.getAccessTokenScopes()))")
    @Mapping(target = "oidcIdToken", expression = "java(toStoredToken(entity.getOidcIdTokenValue(), entity.getOidcIdTokenHash(), entity.getOidcIdTokenIssuedAt(), entity.getOidcIdTokenExpiresAt(), entity.getOidcIdTokenMetadata(), null, null))")
    @Mapping(target = "refreshToken", expression = "java(toStoredToken(entity.getRefreshTokenValue(), entity.getRefreshTokenHash(), entity.getRefreshTokenIssuedAt(), entity.getRefreshTokenExpiresAt(), entity.getRefreshTokenMetadata(), null, null))")
    @Mapping(target = "userCode", expression = "java(toHashedToken(entity.getUserCodeHash(), entity.getUserCodeIssuedAt(), entity.getUserCodeExpiresAt(), entity.getUserCodeMetadata()))")
    @Mapping(target = "deviceCode", expression = "java(toHashedToken(entity.getDeviceCodeHash(), entity.getDeviceCodeIssuedAt(), entity.getDeviceCodeExpiresAt(), entity.getDeviceCodeMetadata()))")
    OAuth2Authorization toDomain(OAuth2AuthorizationEntity entity);

    @Mapping(target = "authorizationCodeHash", source = "authorizationCode.hash")
    @Mapping(target = "authorizationCodeIssuedAt", source = "authorizationCode.issuedAt")
    @Mapping(target = "authorizationCodeExpiresAt", source = "authorizationCode.expiresAt")
    @Mapping(target = "authorizationCodeMetadata", source = "authorizationCode.metadata")

    @Mapping(target = "accessTokenValue", source = "accessToken.value")
    @Mapping(target = "accessTokenHash", source = "accessToken.hash")
    @Mapping(target = "accessTokenIssuedAt", source = "accessToken.issuedAt")
    @Mapping(target = "accessTokenExpiresAt", source = "accessToken.expiresAt")
    @Mapping(target = "accessTokenMetadata", source = "accessToken.metadata")
    @Mapping(target = "accessTokenType", source = "accessToken.tokenType")
    @Mapping(target = "accessTokenScopes", source = "accessToken.scopes")

    @Mapping(target = "oidcIdTokenValue", source = "oidcIdToken.value")
    @Mapping(target = "oidcIdTokenHash", source = "oidcIdToken.hash")
    @Mapping(target = "oidcIdTokenIssuedAt", source = "oidcIdToken.issuedAt")
    @Mapping(target = "oidcIdTokenExpiresAt", source = "oidcIdToken.expiresAt")
    @Mapping(target = "oidcIdTokenMetadata", source = "oidcIdToken.metadata")

    @Mapping(target = "refreshTokenValue", source = "refreshToken.value")
    @Mapping(target = "refreshTokenHash", source = "refreshToken.hash")
    @Mapping(target = "refreshTokenIssuedAt", source = "refreshToken.issuedAt")
    @Mapping(target = "refreshTokenExpiresAt", source = "refreshToken.expiresAt")
    @Mapping(target = "refreshTokenMetadata", source = "refreshToken.metadata")

    @Mapping(target = "userCodeHash", source = "userCode.hash")
    @Mapping(target = "userCodeIssuedAt", source = "userCode.issuedAt")
    @Mapping(target = "userCodeExpiresAt", source = "userCode.expiresAt")
    @Mapping(target = "userCodeMetadata", source = "userCode.metadata")

    @Mapping(target = "deviceCodeHash", source = "deviceCode.hash")
    @Mapping(target = "deviceCodeIssuedAt", source = "deviceCode.issuedAt")
    @Mapping(target = "deviceCodeExpiresAt", source = "deviceCode.expiresAt")
    @Mapping(target = "deviceCodeMetadata", source = "deviceCode.metadata")

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    OAuth2AuthorizationEntity toEntity(OAuth2Authorization domain);

    default HashedToken toHashedToken(String hash,
                                     java.time.OffsetDateTime issuedAt,
                                     java.time.OffsetDateTime expiresAt,
                                     Map<String, Object> metadata) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        return new HashedToken(hash, issuedAt, expiresAt, metadata);
    }

    default StoredToken toStoredToken(String value,
                                     String hash,
                                     java.time.OffsetDateTime issuedAt,
                                     java.time.OffsetDateTime expiresAt,
                                     Map<String, Object> metadata,
                                     String tokenType,
                                     String scopes) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (hash == null || hash.isBlank()) {
            return null;
        }
        return new StoredToken(value, hash, issuedAt, expiresAt, metadata, tokenType, scopes);
    }
}
