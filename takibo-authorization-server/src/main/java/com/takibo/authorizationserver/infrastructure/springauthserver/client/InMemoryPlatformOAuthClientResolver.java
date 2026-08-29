package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ClientType;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Source PLATFORM in-memory de développement : ne résout que {@code postman-client}
 * (TAS-GRANTS-01).
 * <p>
 * Construite par {@link ResolvedOAuthClientResolverConfig}, réservée aux profils {@code dev}
 * et {@code test} — en dehors, cette source est absente du contexte et seule la source TMS
 * ({@link JpaResolvedOAuthClientResolver}) reste active, conformément au périmètre du récit.
 * Ni celle-ci ni {@link JpaResolvedOAuthClientResolver} ne portent {@code @Component} : le
 * composite assemblé par la configuration est marqué {@link org.springframework.context.annotation.Primary},
 * pour qu'il gagne sans ambiguïté même si les deux sources restent, elles aussi, candidates
 * par type. Remplace l'ancien {@code InMemoryDevRegisteredClientConfiguration}
 * (retiré) : {@code postman-client} n'a plus qu'une seule représentation, consommée aussi
 * bien par {@code TakiboRegisteredClientRepository} que par {@code TenantResolutionFilter}
 * et {@code PkceEnforcementFilter}.
 */
public class InMemoryPlatformOAuthClientResolver implements ResolvedOAuthClientResolver {

    private static final String CLIENT_ID = "postman-client";

    private final ResolvedOAuthClient postmanClient;

    public InMemoryPlatformOAuthClientResolver(
            PasswordEncoder passwordEncoder,
            String postmanClientSecret) {
        this.postmanClient = new ResolvedOAuthClient(
                UUID.randomUUID().toString(),
                CLIENT_ID,
                ClientPlan.PLATFORM,
                null,
                null,
                ClientType.CONFIDENTIAL,
                false,
                false,
                true,
                passwordEncoder.encode(postmanClientSecret),
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue(),
                null,
                null,
                null,
                null,
                null,
                null,
                Set.of("api.read", "api.write"),
                Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue()),
                Set.of(),
                Set.of());
    }

    @Override
    public Optional<ResolvedOAuthClient> resolve(String clientId) {
        return CLIENT_ID.equals(clientId) ? Optional.of(postmanClient) : Optional.empty();
    }
}
