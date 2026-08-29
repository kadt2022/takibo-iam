package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientGrantTypeRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientLookupRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientPostLogoutRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientScopeRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/**
 * Câble le {@link ResolvedOAuthClientResolver} effectif de TAS (TAS-GRANTS-01) : source
 * PLATFORM (in-memory, profil {@code dev} uniquement) d'abord si présente dans le contexte,
 * puis source TMS ({@code oauth2_clients}).
 * <p>
 * {@link InMemoryPlatformOAuthClientResolver} et {@link JpaResolvedOAuthClientResolver} ne
 * portent pas {@code @Component} : cette configuration est le seul endroit qui les construit,
 * et le seul bean exposé comme {@link ResolvedOAuthClientResolver} est le composite qu'elle
 * assemble. Les enregistrer aussi comme {@code @Component} créerait trois candidats du même
 * type dès qu'un consommateur demanderait ce port par autowiring — deux sources plus le
 * composite — pour une ambiguïté que Spring ne peut pas résoudre seul.
 * <p>
 * Ce bean n'est pas encore consommé par {@code TakiboRegisteredClientRepository}, {@code
 * TenantResolutionFilter} ni {@code PkceEnforcementFilter} — leur bascule est une tranche
 * séparée.
 */
@Configuration
public class ResolvedOAuthClientResolverConfig {

    @Bean
    @Profile("dev")
    public InMemoryPlatformOAuthClientResolver inMemoryPlatformOAuthClientResolver(
            PasswordEncoder passwordEncoder,
            @Value("${takibo.dev.postman-client.secret}") String postmanClientSecret) {
        return new InMemoryPlatformOAuthClientResolver(passwordEncoder, postmanClientSecret);
    }

    @Bean
    public JpaResolvedOAuthClientResolver jpaResolvedOAuthClientResolver(
            OAuth2ClientLookupRepository clients,
            OAuth2ClientGrantTypeRepository grantTypes,
            OAuth2ClientScopeRepository scopes,
            OAuth2ClientRedirectUriRepository redirectUris,
            OAuth2ClientPostLogoutRedirectUriRepository postLogoutRedirectUris,
            Clock clock) {
        return new JpaResolvedOAuthClientResolver(
                clients, grantTypes, scopes, redirectUris, postLogoutRedirectUris, clock);
    }

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
