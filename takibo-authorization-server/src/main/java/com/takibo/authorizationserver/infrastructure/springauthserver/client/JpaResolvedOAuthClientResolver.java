package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ClientType;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientLookupEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientGrantTypeRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientLookupRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientPostLogoutRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientScopeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Source TMS : clients persistés dans {@code oauth2_clients} (TAS-GRANTS-01).
 * <p>
 * Depuis TMS-OAUTH-CLIENT-BOUNDARY-01, {@code org_id} et {@code space_id} sont nullables et
 * portent la frontiere du client : cette source resout donc les trois plans, deduits de la
 * paire par {@link ClientPlan#of}. Une paire incoherente est traitee comme un client
 * introuvable.
 * <p>
 * Miroir de {@link TakiboRegisteredClientRepository}, dont c'est appelé à prendre la place
 * une fois les trois consommateurs branchés sur {@link ResolvedOAuthClientResolver} : mêmes
 * cinq dépôts, même lecture, seule la forme du résultat diffère.
 * <p>
 * <b>Instantané cohérent.</b> Les cinq lectures (client, grants, scopes, URI de redirection,
 * URI de post-déconnexion) portent sur des tables séparées. Sans transaction englobante,
 * une modification concurrente entre deux de ces lectures produirait un
 * {@link ResolvedOAuthClient} mélangeant l'ancienne et la nouvelle configuration —
 * {@code REPEATABLE READ} garantit que les cinq lectures voient le même instantané.
 */
@Slf4j
public class JpaResolvedOAuthClientResolver implements ResolvedOAuthClientResolver {

    private final OAuth2ClientLookupRepository clients;
    private final OAuth2ClientGrantTypeRepository grantTypes;
    private final OAuth2ClientScopeRepository scopes;
    private final OAuth2ClientRedirectUriRepository redirectUris;
    private final OAuth2ClientPostLogoutRedirectUriRepository postLogoutRedirectUris;
    private final Clock clock;

    public JpaResolvedOAuthClientResolver(OAuth2ClientLookupRepository clients,
                                          OAuth2ClientGrantTypeRepository grantTypes,
                                          OAuth2ClientScopeRepository scopes,
                                          OAuth2ClientRedirectUriRepository redirectUris,
                                          OAuth2ClientPostLogoutRedirectUriRepository postLogoutRedirectUris,
                                          Clock clock) {
        this.clients = clients;
        this.grantTypes = grantTypes;
        this.scopes = scopes;
        this.redirectUris = redirectUris;
        this.postLogoutRedirectUris = postLogoutRedirectUris;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ResolvedOAuthClient> resolve(String clientId) {
        return clients.findByClientId(clientId).flatMap(this::toResolvedClient);
    }

    private Optional<ResolvedOAuthClient> toResolvedClient(OAuth2ClientLookupEntity entity) {
        Set<String> grants = grantTypes.findByClientId(entity.getId()).stream()
                .map(g -> g.getGrantType())
                .collect(Collectors.toUnmodifiableSet());

        if (grants.isEmpty()) {
            // Un client sans grant type est inutilisable : traite comme introuvable plutot
            // que de laisser le constructeur de ResolvedOAuthClient lever plus loin.
            log.warn("OAuth2 client {} ({}) has no grant types; treated as not found",
                    entity.getClientId(), entity.getId());
            return Optional.empty();
        }

        if (isSecretExpired(entity)) {
            // Un secret expire ne doit jamais continuer a authentifier son client : traite
            // comme introuvable, au meme titre qu'un client sans grant type.
            log.warn("OAuth2 client {} ({}) has an expired client secret; treated as not found",
                    entity.getClientId(), entity.getId());
            return Optional.empty();
        }

        boolean requireClientSecret = Boolean.TRUE.equals(entity.getRequireClientSecret());

        // La frontiere est deduite de la paire portee par la ligne, plus jamais supposee
        // SPACE (TMS-OAUTH-CLIENT-BOUNDARY-01). Une paire incoherente leve plutot que de
        // produire un plan approximatif ; elle est traitee comme un client introuvable, au
        // meme titre qu'un client sans grant type.
        ClientPlan plan;
        try {
            plan = ClientPlan.of(entity.getOrgId(), entity.getSpaceId());
        } catch (IllegalArgumentException e) {
            log.warn("OAuth2 client {} ({}) has an inconsistent boundary; treated as not found: {}",
                    entity.getClientId(), entity.getId(), e.getMessage());
            return Optional.empty();
        }

        try {
            return Optional.of(new ResolvedOAuthClient(
                    entity.getId().toString(),
                    entity.getClientId(),
                    plan,
                    entity.getOrgId(),
                    entity.getSpaceId(),
                    toDomainClientType(entity.getClientType()),
                    Boolean.TRUE.equals(entity.getRequirePkce()),
                    Boolean.TRUE.equals(entity.getRequireConsent()),
                    requireClientSecret,
                    entity.getClientSecretHash(),
                    entity.getTokenEndpointAuthMethod(),
                    entity.getJwksUri(),
                    entity.getJwksJson(),
                    entity.getIdTokenSignedAlg(),
                    ttlOf(entity.getAccessTokenTtlSeconds()),
                    ttlOf(entity.getRefreshTokenTtlSeconds()),
                    ttlOf(entity.getIdTokenTtlSeconds()),
                    scopes.findByClientId(entity.getId()).stream()
                            .map(s -> s.getScope())
                            .collect(Collectors.toUnmodifiableSet()),
                    grants,
                    redirectUris.findByClientId(entity.getId()).stream()
                            .map(r -> r.getUri())
                            .collect(Collectors.toUnmodifiableSet()),
                    postLogoutRedirectUris.findByClientId(entity.getId()).stream()
                            .map(r -> r.getUri())
                            .collect(Collectors.toUnmodifiableSet())));
        } catch (IllegalArgumentException e) {
            // Une configuration incoherente en base (ex. secret requis sans hash) ne doit
            // jamais faire remonter une erreur opaque : traite comme introuvable, au meme
            // titre qu'un client sans grant type.
            log.warn("OAuth2 client {} ({}) failed resolution: {}",
                    entity.getClientId(), entity.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isSecretExpired(OAuth2ClientLookupEntity entity) {
        // Un client qui n'utilise aucun secret (private_key_jwt, par exemple) n'a rien a
        // expirer : une ancienne valeur residuelle en base ne doit pas le rejeter. La borne
        // est inclusive - l'instant exact d'expiration compte deja comme expire, pas
        // seulement l'instant qui le suit.
        OffsetDateTime expiresAt = entity.getClientSecretExpiresAt();
        return Boolean.TRUE.equals(entity.getRequireClientSecret())
                && expiresAt != null
                && !expiresAt.toInstant().isAfter(Instant.now(clock));
    }

    private static ClientType toDomainClientType(OAuth2ClientLookupEntity.ClientType entityType) {
        return ClientType.valueOf(entityType.name());
    }

    private static Duration ttlOf(Integer seconds) {
        return seconds == null ? null : Duration.ofSeconds(seconds);
    }
}
