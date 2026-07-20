package com.takibo.authorizationserver.infrastructure.springauthserver.client;

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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * {@link RegisteredClientRepository} adossé à la table {@code oauth2_clients} (clients SPACE).
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
        return clients.findByClientId(clientId)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

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
}
