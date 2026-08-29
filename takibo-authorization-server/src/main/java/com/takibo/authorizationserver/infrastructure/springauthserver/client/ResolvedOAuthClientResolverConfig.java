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
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/**
 * Câble le {@link ResolvedOAuthClientResolver} effectif de TAS (TAS-GRANTS-01) : source
 * PLATFORM (in-memory, profils {@code dev} et {@code test} uniquement) d'abord si présente
 * dans le contexte, puis source TMS ({@code oauth2_clients}).
 * <p>
 * {@code test} rejoint {@code dev} pour la même raison qu'ailleurs dans ce module —
 * {@code takibo.tas.keys.ephemeral} suit la même règle : le filet de sécurité TAS-GRANTS-00
 * tourne sous le profil {@code test} et s'appuie sur {@code postman-client} pour ses
 * scénarios PLATFORM. L'exclure de {@code test} n'exclurait que la couverture, jamais
 * la production, qui n'active aucun des deux.
 * <p>
 * {@link InMemoryPlatformOAuthClientResolver} et {@link JpaResolvedOAuthClientResolver} ne
 * portent pas {@code @Component} : cette configuration est le seul endroit qui les construit.
 * Cela ne suffit pourtant pas à garantir l'unicité du bean {@link ResolvedOAuthClientResolver} :
 * les deux sources implémentent aussi cette interface, et Spring type-matche l'autowiring sur
 * la classe réelle du bean, pas seulement sur le type de retour déclaré de sa méthode
 * {@code @Bean}. Sans {@link Primary} sur le composite ci-dessous, trois candidats du même
 * type existent dès qu'un consommateur le demande par autowiring ; ce n'est que la
 * correspondance fortuite entre le nom du paramètre et le nom du bean qui a évité l'ambiguïté
 * jusqu'ici — fragile au premier renommage ou au premier consommateur dont le paramètre
 * porterait un autre nom. {@link Primary} rend le choix explicite et indépendant du nommage.
 */
@Configuration
public class ResolvedOAuthClientResolverConfig {

    @Bean
    @Profile({"dev", "test"})
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
    @Primary
    public ResolvedOAuthClientResolver resolvedOAuthClientResolver(
            ObjectProvider<InMemoryPlatformOAuthClientResolver> platformResolver,
            JpaResolvedOAuthClientResolver tmsResolver) {
        InMemoryPlatformOAuthClientResolver platform = platformResolver.getIfAvailable();
        return platform == null
                ? new CompositeResolvedOAuthClientResolver(tmsResolver)
                : new CompositeResolvedOAuthClientResolver(platform, tmsResolver);
    }
}
