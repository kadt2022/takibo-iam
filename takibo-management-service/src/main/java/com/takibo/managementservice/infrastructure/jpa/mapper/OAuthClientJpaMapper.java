package com.takibo.managementservice.infrastructure.jpa.mapper;

import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.domain.vo.OAuthClientId;
import com.takibo.managementservice.infrastructure.entity.*;
import org.mapstruct.*;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OAuthClientJpaMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "spaceId", source = "spaceId")
    @Mapping(target = "space", expression = "java(spaceRef.getReference(map(client.getSpaceId())))")
    @Mapping(target = "clientIdIssuedAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "tokenEndpointAuthMethod", source = "tokenEndpointAuthMethod")
    @Mapping(target = "scopes", ignore = true)
    @Mapping(target = "grantTypes", ignore = true)
    @Mapping(target = "redirectUris", ignore = true)
    @Mapping(target = "postLogoutRedirectUris", ignore = true)
    @Mapping(target = "corsOrigins", ignore = true)
    @Mapping(target = "secretHistory", ignore = true)
    OAuth2ClientEntity toEntity(OAuthClient client, @Context SpaceRef spaceRef);

    @AfterMapping
    default void fillChildren(OAuthClient src, @MappingTarget OAuth2ClientEntity dst) {
        UUID orgId = dst.getOrgId();
        UUID spaceId = dst.getSpaceId();
        UUID clientId = dst.getId();

        for (String s : src.getScopes()) {
            dst.getScopes().add(OAuth2ClientScopeEntity.builder()
                    .id(UUID.randomUUID())
                    .orgId(orgId)
                    .spaceId(spaceId)
                    .clientId(clientId)
                    .client(dst)
                    .scope(s)
                    .build());
        }
        for (String g : src.getGrantTypes()) {
            dst.getGrantTypes().add(OAuth2ClientGrantTypeEntity.builder()
                    .id(UUID.randomUUID())
                    .orgId(orgId)
                    .spaceId(spaceId)
                    .clientId(clientId)
                    .client(dst)
                    .grantType(g)
                    .build());
        }
        for (String u : src.getRedirectUris()) {
            dst.getRedirectUris().add(OAuth2ClientRedirectUriEntity.builder()
                    .id(UUID.randomUUID())
                    .orgId(orgId)
                    .spaceId(spaceId)
                    .clientId(clientId)
                    .client(dst)
                    .uri(u)
                    .build());
        }
        for (String u : src.getPostLogoutRedirectUris()) {
            dst.getPostLogoutRedirectUris().add(OAuth2ClientPostLogoutRedirectUriEntity.builder()
                    .id(UUID.randomUUID())
                    .orgId(orgId)
                    .spaceId(spaceId)
                    .clientId(clientId)
                    .client(dst)
                    .uri(u)
                    .build());
        }
        for (String o : src.getCorsOrigins()) {
            dst.getCorsOrigins().add(OAuth2ClientCorsOriginEntity.builder()
                    .id(UUID.randomUUID())
                    .orgId(orgId)
                    .spaceId(spaceId)
                    .clientId(clientId)
                    .client(dst)
                    .origin(o)
                    .build());
        }
    }

    // ========== Entity -> Domain ==========
    @Mapping(target = "id",      source = "id")
    @Mapping(target = "spaceId", source = "space")
    @Mapping(target = "tokenEndpointAuthMethod", source = "tokenEndpointAuthMethod")
    @Mapping(target = "scopes", expression = "java(toScopes(e))")
    @Mapping(target = "grantTypes", expression = "java(toGrants(e))")
    @Mapping(target = "redirectUris", expression = "java(toRedirects(e))")
    @Mapping(target = "postLogoutRedirectUris", expression = "java(toPostLogout(e))")
    @Mapping(target = "corsOrigins", expression = "java(toOrigins(e))")
    OAuthClient toDomain(OAuth2ClientEntity e);

    // ========== Helpers List -> Set ==========
    default Set<String> toScopes(OAuth2ClientEntity e){
        return e.getScopes().stream().map(OAuth2ClientScopeEntity::getScope).collect(Collectors.toUnmodifiableSet());
    }
    default Set<String> toGrants(OAuth2ClientEntity e){
        return e.getGrantTypes().stream().map(OAuth2ClientGrantTypeEntity::getGrantType).collect(Collectors.toUnmodifiableSet());
    }
    default Set<String> toRedirects(OAuth2ClientEntity e){
        return e.getRedirectUris().stream().map(OAuth2ClientRedirectUriEntity::getUri).collect(Collectors.toUnmodifiableSet());
    }
    default Set<String> toPostLogout(OAuth2ClientEntity e){
        return e.getPostLogoutRedirectUris().stream().map(OAuth2ClientPostLogoutRedirectUriEntity::getUri).collect(Collectors.toUnmodifiableSet());
    }
    default Set<String> toOrigins(OAuth2ClientEntity e){
        return e.getCorsOrigins().stream().map(OAuth2ClientCorsOriginEntity::getOrigin).collect(Collectors.toUnmodifiableSet());
    }

    // ========== Helpers de conversion (clé !) ==========
    // Domain -> raw
    default UUID map(OAuthClientId id) { return id == null ? null : id.getValue(); }
    default UUID map(SpaceId id)       { return id == null ? null : id.value(); }
    default String map(TokenEndpointAuthMethod m) { return m == null ? null : m.name(); }

    // raw -> Domain (utilise les factories, pas les constructeurs)
    default OAuthClientId map(UUID id) {
        return id == null ? null : OAuthClientId.of(id);
    }
    default SpaceId map(SpaceEntity space) {
        return (space == null || space.getId() == null) ? null : SpaceId.of(space.getId());
    }
    default TokenEndpointAuthMethod map(String method) {
        return method == null ? null : TokenEndpointAuthMethod.valueOf(method);
    }
}




