package com.takibo.managementservice.domain.model;

import com.takibo.managementservice.domain.vo.OAuthClientId;
import com.takibo.managementservice.domain.vo.SpaceId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true)
public class OAuthClient {

    @EqualsAndHashCode.Include
    private final OAuthClientId id;

    private final UUID orgId;
    private final SpaceId spaceId;

    private final String clientId;
    private final String clientName;

    private final ClientType clientType;
    @Builder.Default
    private final boolean requireClientSecret = false;

    private final String clientSecretHash;
    private final Instant clientSecretExpiresAt;

    private final TokenEndpointAuthMethod tokenEndpointAuthMethod;

    @Builder.Default
    private final boolean requirePkce = false;

    @Builder.Default
    private final boolean requireConsent = false;

    private final String jwksUri;
    private final String jwksJson;
    private final String idTokenSignedAlg;

    private final Integer accessTokenTtlSeconds;
    private final Integer refreshTokenTtlSeconds;
    private final Integer idTokenTtlSeconds;

    @Builder.Default
    private final Set<String> scopes = Collections.emptySet();

    @Builder.Default
    private final Set<String> grantTypes = Collections.emptySet();

    @Builder.Default
    private final Set<String> redirectUris = Collections.emptySet();

    @Builder.Default
    private final Set<String> postLogoutRedirectUris = Collections.emptySet();

    @Builder.Default
    private final Set<String> corsOrigins = Collections.emptySet();

    @Builder.Default
    private final Map<String, Object> additionalSettings = Collections.emptyMap();

    @Builder.Default
    private final Instant createdAt = Instant.now();

    @Builder.Default
    private final Instant updatedAt = Instant.now();

    @Builder.Default
    private final Long version = 0L;

    // ===== FACTORY METHODS =====

    public static OAuthClient create(UUID orgId, SpaceId spaceId, String clientId, String clientName, ClientType type) {
        return OAuthClient.builder()
                .id(OAuthClientId.newId())
                .orgId(orgId)
                .spaceId(spaceId)
                .clientId(clientId)
                .clientName(clientName)
                .clientType(type)
                .build();
    }

    // ===== BUSINESS METHODS =====

    public OAuthClient withSecret(String hash, Instant exp) {
        return this.toBuilder()
                .requireClientSecret(true)
                .clientSecretHash(hash)
                .clientSecretExpiresAt(exp)
                .build();
    }
}