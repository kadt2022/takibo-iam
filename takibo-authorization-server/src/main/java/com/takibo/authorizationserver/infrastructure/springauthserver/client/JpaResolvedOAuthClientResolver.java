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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Source TMS : clients SPACE persistés dans {@code oauth2_clients} (TAS-GRANTS-01).
 * <p>
 * {@code org_id} et {@code space_id} y sont tous deux obligatoires — aucun client
 * ORGANIZATION-only n'existe encore dans ce schéma. Cette source ne résout donc que des
 * clients {@link ClientPlan#SPACE} ; représenter ORGANIZATION exige une migration de schéma
 * séparée, hors périmètre de ce récit.
 * <p>
 * Miroir de {@link TakiboRegisteredClientRepository}, dont c'est appelé à prendre la place
 * une fois les trois consommateurs branchés sur {@link ResolvedOAuthClientResolver} : mêmes
 * cinq dépôts, même lecture, seule la forme du résultat diffère.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JpaResolvedOAuthClientResolver implements ResolvedOAuthClientResolver {

    private final OAuth2ClientLookupRepository clients;
    private final OAuth2ClientGrantTypeRepository grantTypes;
    private final OAuth2ClientScopeRepository scopes;
    private final OAuth2ClientRedirectUriRepository redirectUris;
    private final OAuth2ClientPostLogoutRedirectUriRepository postLogoutRedirectUris;

    @Override
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

        boolean requireClientSecret = Boolean.TRUE.equals(entity.getRequireClientSecret());
        ClientType clientType = requireClientSecret ? ClientType.CONFIDENTIAL : ClientType.PUBLIC;

        try {
            return Optional.of(new ResolvedOAuthClient(
                    entity.getId().toString(),
                    entity.getClientId(),
                    ClientPlan.SPACE,
                    entity.getOrgId(),
                    entity.getSpaceId(),
                    clientType,
                    Boolean.TRUE.equals(entity.getRequirePkce()),
                    // Pas encore de colonne require_consent projetee dans cette lecture : la
                    // meme absence que TakiboRegisteredClientRepository, qui ne la lit pas
                    // non plus aujourd'hui.
                    false,
                    requireClientSecret,
                    entity.getClientSecretHash(),
                    entity.getTokenEndpointAuthMethod(),
                    entity.getJwksUri(),
                    entity.getJwksJson(),
                    entity.getIdTokenSignedAlg(),
                    null,
                    null,
                    null,
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
}
