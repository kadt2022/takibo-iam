package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientContextHolder;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientLookupEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientGrantTypeRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientLookupRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientPostLogoutRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientScopeRepository;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.TakiboTokenClaims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * {@link RegisteredClientRepository} adossé à la table {@code oauth2_clients} (clients SPACE).
 * <p>
 * {@code findByClientId} reconstruit le {@link RegisteredClient} uniquement depuis le
 * {@link ResolvedOAuthClient} rendu par {@link ResolvedOAuthClientResolver} (TAS-GRANTS-01) —
 * jamais un second appel de son côté quand un premier a déjà eu lieu dans la même requête.
 * {@code TenantResolutionFilter} pose ce résultat dans {@link ResolvedOAuthClientContextHolder}
 * avant que Spring Authorization Server n'atteigne ce dépôt ; {@code findByClientId} le
 * réutilise s'il correspond au {@code client_id} demandé, et ne résout directement que
 * lorsqu'il est appelé hors de cette requête filtrée (le holder est alors vide). Une seule
 * lecture, une seule vérité : deux résolutions séparées pourraient sinon observer deux états
 * différents si une modification survenait entre les deux.
 * <p>
 * {@code findById} reste sur la lecture directe : {@link ResolvedOAuthClientResolver} résout
 * délibérément par {@code client_id} public et rien d'autre — l'identifiant technique que SAS
 * utilise pour recharger le client d'une autorisation persistée n'entre pas dans ce contrat.
 * <p>
 * Le tenant réel du client ({@code org_id}, {@code space_id}) est placé dans les
 * {@link ClientSettings}, où {@code TakiboOAuth2TokenCustomizer} le relit sans second accès DB.
 * <p>
 * Lecture seule : l'enregistrement des clients passe par le management-service
 * ({@code OAuthClientController}), jamais par ce chemin.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TakiboRegisteredClientRepository implements RegisteredClientRepository {

    private final OAuth2ClientLookupRepository clients;
    private final OAuth2ClientScopeRepository scopes;
    private final OAuth2ClientGrantTypeRepository grantTypes;
    private final OAuth2ClientRedirectUriRepository redirectUris;
    private final OAuth2ClientPostLogoutRedirectUriRepository postLogoutRedirectUris;
    private final ResolvedOAuthClientResolver resolvedOAuthClientResolver;

    @Override
    public void save(RegisteredClient registeredClient) {
        // Lecture seule : la création/MAJ des clients est la responsabilité du management-service.
        throw new UnsupportedOperationException(
                "OAuth2 clients are managed via the management-service, not the authorization server");
    }

    @Override
    public RegisteredClient findById(String id) {
        return clients.findById(UUID.fromString(id))
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        ResolvedOAuthClient fromRequestContext = ResolvedOAuthClientContextHolder.get();
        if (fromRequestContext != null) {
            // Un contexte posé mais portant un autre client_id est une divergence, pas une
            // absence : on échoue fermé plutôt que d'aller résoudre B en base pendant qu'un
            // contexte pour A est actif, ce qui romprait la garantie d'une seule résolution
            // par requête sans qu'aucun appelant ne s'en aperçoive.
            return fromRequestContext.clientId().equals(clientId)
                    ? toRegisteredClient(fromRequestContext)
                    : null;
        }
        return resolvedOAuthClientResolver.resolve(clientId)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    /** Chemin direct, pour {@link #findById} seul — voir la javadoc de classe. */
    private RegisteredClient toRegisteredClient(OAuth2ClientLookupEntity entity) {
        List<AuthorizationGrantType> grants = grantTypes.findByClientId(entity.getId()).stream()
                .map(g -> new AuthorizationGrantType(g.getGrantType()))
                .toList();

        if (grants.isEmpty()) {
            // Un client sans grant type est inutilisable : on le traite comme introuvable plutôt
            // que de laisser RegisteredClient.build() lever une erreur opaque côté SAS.
            log.warn("OAuth2 client {} ({}) has no grant types; treated as not found",
                    entity.getClientId(), entity.getId());
            return null;
        }

        ClientSettings.Builder clientSettings = ClientSettings.builder()
                .requireProofKey(Boolean.TRUE.equals(entity.getRequirePkce()))
                .setting(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_SPACE)
                .setting(TakiboTokenClaims.TENANT_SOURCE, TakiboTokenClaims.SOURCE_OAUTH2_CLIENT)
                .setting(TakiboTokenClaims.ORG_ID, entity.getOrgId().toString())
                .setting(TakiboTokenClaims.SPACE_ID, entity.getSpaceId().toString());

        if (StringUtils.hasText(entity.getJwksUri())) {
            clientSettings.jwkSetUrl(entity.getJwksUri());
        }
        if (StringUtils.hasText(entity.getJwksJson())) {
            clientSettings.setting(TakiboJwtClientAssertionDecoderFactory.JWK_SET_JSON_SETTING,
                    entity.getJwksJson());
        }
        if (StringUtils.hasText(entity.getIdTokenSignedAlg())) {
            SignatureAlgorithm signingAlgorithm = SignatureAlgorithm.from(entity.getIdTokenSignedAlg());
            if (signingAlgorithm != null) {
                clientSettings.tokenEndpointAuthenticationSigningAlgorithm(signingAlgorithm);
            } else {
                log.warn("OAuth2 client {} ({}) has an unsupported signing algorithm: {}",
                        entity.getClientId(), entity.getId(), entity.getIdTokenSignedAlg());
            }
        }

        RegisteredClient.Builder builder = RegisteredClient.withId(entity.getId().toString())
                .clientId(entity.getClientId())
                .clientAuthenticationMethod(new ClientAuthenticationMethod(entity.getTokenEndpointAuthMethod()))
                .clientSettings(clientSettings.build());

        grants.forEach(builder::authorizationGrantType);
        scopes.findByClientId(entity.getId()).forEach(s -> builder.scope(s.getScope()));
        // Requis par SAS pour les clients authorization_code (sinon build() échoue).
        redirectUris.findByClientId(entity.getId()).forEach(r -> builder.redirectUri(r.getUri()));
        postLogoutRedirectUris.findByClientId(entity.getId()).forEach(r -> builder.postLogoutRedirectUri(r.getUri()));

        if (Boolean.TRUE.equals(entity.getRequireClientSecret()) && entity.getClientSecretHash() != null) {
            // Le hash est passé tel quel : il a été produit par le même PasswordEncoder partagé.
            builder.clientSecret(entity.getClientSecretHash());
        }

        return builder.build();
    }

    /**
     * Chemin canonique, pour {@link #findByClientId} — aucun accès direct aux cinq dépôts TMS.
     * <p>
     * {@code requireProofKey} porte la valeur brute {@code require_pkce}, pas
     * {@link ResolvedOAuthClient#pkceRequired()} : c'est le réglage natif que Spring
     * Authorization Server lit pour son propre contrôle PKCE, distinct de la règle plus
     * stricte qu'applique {@code PkceEnforcementFilter} (qui, elle, force PKCE pour tout
     * client {@code PUBLIC}). Même distinction que l'ancien chemin par entité.
     * <p>
     * {@code requireConsent} et les trois TTL sont appliqués ici — {@code accessTokenTtl} et
     * {@code refreshTokenTtl} ont un réglage natif dans {@link TokenSettings} ;
     * {@code idTokenTtl} n'en a pas, et est porté sous
     * {@link TakiboTokenClaims#ID_TOKEN_TTL_SECONDS}, que {@code TakiboOAuth2TokenCustomizer}
     * relit pour réécrire l'expiration de l'ID token à l'émission. Un TTL absent laisse le
     * défaut de Spring Authorization Server, jamais une valeur figée ici.
     */
    private RegisteredClient toRegisteredClient(ResolvedOAuthClient client) {
        ClientSettings.Builder clientSettings = ClientSettings.builder()
                .requireProofKey(client.requireProofKey())
                .requireAuthorizationConsent(client.requireConsent())
                .setting(TakiboTokenClaims.SCOPE_LEVEL, client.plan().claimValue())
                .setting(TakiboTokenClaims.TENANT_SOURCE, client.plan().tenantSource());
        if (client.orgId() != null) {
            clientSettings.setting(TakiboTokenClaims.ORG_ID, client.orgId().toString());
        }
        if (client.spaceId() != null) {
            clientSettings.setting(TakiboTokenClaims.SPACE_ID, client.spaceId().toString());
        }
        if (StringUtils.hasText(client.jwksUri())) {
            clientSettings.jwkSetUrl(client.jwksUri());
        }
        if (StringUtils.hasText(client.jwksJson())) {
            clientSettings.setting(TakiboJwtClientAssertionDecoderFactory.JWK_SET_JSON_SETTING,
                    client.jwksJson());
        }
        if (StringUtils.hasText(client.idTokenSignedAlg())) {
            SignatureAlgorithm signingAlgorithm = SignatureAlgorithm.from(client.idTokenSignedAlg());
            if (signingAlgorithm != null) {
                clientSettings.tokenEndpointAuthenticationSigningAlgorithm(signingAlgorithm);
            } else {
                log.warn("OAuth2 client {} has an unsupported signing algorithm: {}",
                        client.clientId(), client.idTokenSignedAlg());
            }
        }

        TokenSettings.Builder tokenSettings = TokenSettings.builder();
        if (client.accessTokenTtl() != null) {
            tokenSettings.accessTokenTimeToLive(client.accessTokenTtl());
        }
        if (client.refreshTokenTtl() != null) {
            tokenSettings.refreshTokenTimeToLive(client.refreshTokenTtl());
        }
        if (client.idTokenTtl() != null) {
            tokenSettings.setting(TakiboTokenClaims.ID_TOKEN_TTL_SECONDS, client.idTokenTtl().toSeconds());
        }

        RegisteredClient.Builder builder = RegisteredClient.withId(client.registeredClientId())
                .clientId(client.clientId())
                .clientAuthenticationMethod(new ClientAuthenticationMethod(client.tokenEndpointAuthMethod()))
                .clientSettings(clientSettings.build())
                .tokenSettings(tokenSettings.build());

        client.grantTypes().forEach(g -> builder.authorizationGrantType(new AuthorizationGrantType(g)));
        client.scopes().forEach(builder::scope);
        // Requis par SAS pour les clients authorization_code (sinon build() échoue).
        client.redirectUris().forEach(builder::redirectUri);
        client.postLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);

        if (client.requireClientSecret()) {
            // Le hash est passé tel quel : il a été produit par le même PasswordEncoder
            // partagé. ResolvedOAuthClient garantit déjà sa présence à la construction.
            builder.clientSecret(client.clientSecretHash());
        }

        return builder.build();
    }
}
