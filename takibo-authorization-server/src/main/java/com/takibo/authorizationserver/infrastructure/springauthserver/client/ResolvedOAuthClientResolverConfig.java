package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Câble le {@link ResolvedOAuthClientResolver} effectif de TAS (TAS-GRANTS-01) : source
 * PLATFORM (in-memory, profil {@code dev} uniquement) d'abord si présente dans le contexte,
 * puis source TMS ({@code oauth2_clients}).
 * <p>
 * {@link InMemoryPlatformOAuthClientResolver} porte {@code @Profile("dev")} : hors de ce
 * profil, Spring ne le construit pas et le paramètre correspondant reste vide. Ce bean n'est
 * pas encore consommé par {@code TakiboRegisteredClientRepository}, {@code TenantResolutionFilter}
 * ni {@code PkceEnforcementFilter} — leur bascule est une tranche séparée.
 */
@Configuration
public class ResolvedOAuthClientResolverConfig {

    @Bean
    public ResolvedOAuthClientResolver resolvedOAuthClientResolver(
            ObjectProvider<InMemoryPlatformOAuthClientResolver> platformResolver,
            JpaResolvedOAuthClientResolver tmsResolver) {
        InMemoryPlatformOAuthClientResolver platform = platformResolver.getIfAvailable();
        return platform == null
                ? new CompositeResolvedOAuthClientResolver(tmsResolver)
                : new CompositeResolvedOAuthClientResolver(platform, tmsResolver);
    }
}
