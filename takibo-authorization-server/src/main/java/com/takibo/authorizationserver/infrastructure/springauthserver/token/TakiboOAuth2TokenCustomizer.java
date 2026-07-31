package com.takibo.authorizationserver.infrastructure.springauthserver.token;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.util.StringUtils;

/**
 * Situe les access tokens à partir du client OAuth2 enregistré — sans rien inventer.
 * <p>
 * Doctrine : TAS ne devine plus de frontière. Le scope ({@code takibo_scope_level}) et le tenant
 * ({@code org_id}/{@code space_id}) sont portés par le {@link RegisteredClient} (ses
 * {@code ClientSettings}, renseignés par le repository). Le customizer ne fait que les transcrire.
 * <p>
 * Plus de fallback : chaque plan et sa provenance doivent être explicites. Un client PLATFORM
 * produit un token sans tenant (fail-closed sur les routes tenant) ; une frontière incomplète
 * ou incompatible est une configuration incohérente -> erreur.
 */
@Configuration
public class TakiboOAuth2TokenCustomizer {

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            applyTenantClaims(context.getRegisteredClient(), context.getClaims());
            applySubjectClaims(context.getAuthorizationGrantType(), context.getClaims());
        };
    }

    /**
     * Transcrit le plan et la frontière portés par le client. TAS ne complète aucune valeur :
     * SPACE exige org_id + space_id, ORGANIZATION exige org_id seul, PLATFORM refuse les deux.
     */
    static void applyTenantClaims(RegisteredClient client, JwtClaimsSet.Builder claims) {
        ClientSettings settings = client.getClientSettings();
        String scopeLevel = settings.getSetting(TakiboTokenClaims.SCOPE_LEVEL);
        String tenantSource = settings.getSetting(TakiboTokenClaims.TENANT_SOURCE);
        String orgId = settings.getSetting(TakiboTokenClaims.ORG_ID);
        String spaceId = settings.getSetting(TakiboTokenClaims.SPACE_ID);

        if (!StringUtils.hasText(scopeLevel)) {
            throw new IllegalStateException(
                    "CLIENT_REQUIRES_EXPLICIT_SCOPE_LEVEL: client " + client.getClientId());
        }
        if (!StringUtils.hasText(tenantSource)) {
            throw new IllegalStateException(
                    "CLIENT_REQUIRES_EXPLICIT_TENANT_SOURCE: client " + client.getClientId());
        }

        switch (scopeLevel) {
        case TakiboTokenClaims.SCOPE_SPACE -> {
            if (!StringUtils.hasText(orgId) || !StringUtils.hasText(spaceId)) {
                throw new IllegalStateException(
                        "SPACE_CLIENT_REQUIRES_ORG_AND_SPACE: client " + client.getClientId());
            }
            claims.claim(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_SPACE)
                    .claim(TakiboTokenClaims.ORG_ID, orgId)
                    .claim(TakiboTokenClaims.SPACE_ID, spaceId)
                    .claim(TakiboTokenClaims.TENANT_SOURCE, tenantSource);
        }
        case TakiboTokenClaims.SCOPE_ORGANIZATION -> {
            if (!StringUtils.hasText(orgId) || StringUtils.hasText(spaceId)) {
                throw new IllegalStateException(
                        "ORGANIZATION_CLIENT_REQUIRES_ORG_WITHOUT_SPACE: client "
                                + client.getClientId());
            }
            claims.claim(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_ORGANIZATION)
                    .claim(TakiboTokenClaims.ORG_ID, orgId)
                    .claim(TakiboTokenClaims.TENANT_SOURCE, tenantSource);
        }
        case TakiboTokenClaims.SCOPE_PLATFORM -> {
            if (StringUtils.hasText(orgId) || StringUtils.hasText(spaceId)) {
                throw new IllegalStateException(
                        "PLATFORM_CLIENT_MUST_NOT_CARRY_TENANT: client "
                                + client.getClientId());
            }
            claims.claim(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_PLATFORM)
                    .claim(TakiboTokenClaims.TENANT_SOURCE, tenantSource);
        }
        default -> throw new IllegalStateException(
                "UNSUPPORTED_CLIENT_SCOPE_LEVEL: " + scopeLevel);
        }
    }

    /**
     * Décrit l'acteur — uniquement pour le flux client_credentials, où le sujet EST le client.
     * On ne ment pas pour les autres grants (ex. authorization_code = un humain).
     */
    static void applySubjectClaims(AuthorizationGrantType grantType, JwtClaimsSet.Builder claims) {
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(grantType)) {
            claims.claim(TakiboTokenClaims.SUBJECT_TYPE, TakiboTokenClaims.SUBJECT_CLIENT_APP)
                    .claim(TakiboTokenClaims.AUTH_METHOD, TakiboTokenClaims.AUTH_CLIENT_CREDENTIALS);
        }
    }
}
