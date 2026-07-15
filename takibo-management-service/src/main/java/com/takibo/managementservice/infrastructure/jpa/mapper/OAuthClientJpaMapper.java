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

    // ========== Domain -> Entity (UPDATE d'un client existant) ==========
    // Piège récurrent du dépôt : toEntity fabrique une entité NEUVE (children avec de
    // nouveaux UUID, version par défaut) — la sauver pour un client existant tente de
    // ré-INSÉRER les enfants et casse les contraintes uniques (ex. uk_ocg_client_grant).
    // L'update doit donc s'appliquer sur l'entité MANAGÉE : scalaires copiés, enfants
    // synchronisés par différence (rien n'est touché quand les valeurs sont identiques,
    // cas de la rotation de secret).
    default void applyDomainState(OAuthClient src, OAuth2ClientEntity dst) {
        UUID orgId = dst.getOrgId();
        UUID spaceId = dst.getSpaceId();
        UUID clientId = dst.getId();

        dst.setClientId(src.getClientId());
        dst.setClientName(src.getClientName());
        dst.setClientType(src.getClientType() == null
                ? null
                : OAuth2ClientEntity.ClientType.valueOf(src.getClientType().name()));
        dst.setRequireClientSecret(src.isRequireClientSecret());
        dst.setClientSecretHash(src.getClientSecretHash());
        dst.setClientSecretExpiresAt(src.getClientSecretExpiresAt());
        dst.setTokenEndpointAuthMethod(map(src.getTokenEndpointAuthMethod()));
        dst.setRequirePkce(src.isRequirePkce());
        dst.setRequireConsent(src.isRequireConsent());
        dst.setJwksUri(src.getJwksUri());
        dst.setJwksJson(src.getJwksJson());
        dst.setIdTokenSignedAlg(src.getIdTokenSignedAlg());
        dst.setAccessTokenTtlSeconds(src.getAccessTokenTtlSeconds());
        dst.setRefreshTokenTtlSeconds(src.getRefreshTokenTtlSeconds());
        dst.setIdTokenTtlSeconds(src.getIdTokenTtlSeconds());
        dst.setAdditionalSettings(src.getAdditionalSettings());

        syncChildren(dst.getScopes(), src.getScopes(),
                OAuth2ClientScopeEntity::getScope,
                v -> OAuth2ClientScopeEntity.builder()
                        .id(UUID.randomUUID()).orgId(orgId).spaceId(spaceId)
                        .clientId(clientId).client(dst).scope(v).build());
        syncChildren(dst.getGrantTypes(), src.getGrantTypes(),
                OAuth2ClientGrantTypeEntity::getGrantType,
                v -> OAuth2ClientGrantTypeEntity.builder()
                        .id(UUID.randomUUID()).orgId(orgId).spaceId(spaceId)
                        .clientId(clientId).client(dst).grantType(v).build());
        syncChildren(dst.getRedirectUris(), src.getRedirectUris(),
                OAuth2ClientRedirectUriEntity::getUri,
                v -> OAuth2ClientRedirectUriEntity.builder()
                        .id(UUID.randomUUID()).orgId(orgId).spaceId(spaceId)
                        .clientId(clientId).client(dst).uri(v).build());
        syncChildren(dst.getPostLogoutRedirectUris(), src.getPostLogoutRedirectUris(),
                OAuth2ClientPostLogoutRedirectUriEntity::getUri,
                v -> OAuth2ClientPostLogoutRedirectUriEntity.builder()
                        .id(UUID.randomUUID()).orgId(orgId).spaceId(spaceId)
                        .clientId(clientId).client(dst).uri(v).build());
        syncChildren(dst.getCorsOrigins(), src.getCorsOrigins(),
                OAuth2ClientCorsOriginEntity::getOrigin,
                v -> OAuth2ClientCorsOriginEntity.builder()
                        .id(UUID.randomUUID()).orgId(orgId).spaceId(spaceId)
                        .clientId(clientId).client(dst).origin(v).build());
    }

    // Synchronisation par différence : supprime ce qui n'est plus déclaré
    // (orphanRemoval fait le DELETE), ajoute ce qui manque, ne touche à rien d'autre.
    default <E> void syncChildren(java.util.List<E> current,
                                  Set<String> target,
                                  java.util.function.Function<E, String> valueOf,
                                  java.util.function.Function<String, E> factory) {
        Set<String> wanted = target == null ? Set.of() : target;
        current.removeIf(e -> !wanted.contains(valueOf.apply(e)));
        Set<String> present = current.stream().map(valueOf).collect(Collectors.toSet());
        for (String v : wanted) {
            if (!present.contains(v)) {
                current.add(factory.apply(v));
            }
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




