package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ClientType;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Source PLATFORM in-memory de développement : ne résout que {@code postman-client}
 * (TAS-GRANTS-01).
 * <p>
 * Réservée au profil {@code dev} — en dehors, cette source est absente du contexte et seule
 * la source TMS ({@link JpaResolvedOAuthClientResolver}) reste active, conformément au
 * périmètre du récit. Mêmes valeurs que l'ancien
 * {@code InMemoryDevRegisteredClientConfiguration} qu'elle vise à remplacer une fois les
 * trois consommateurs branchés sur ce port.
 */
@Component
@Profile("dev")
public class InMemoryPlatformOAuthClientResolver implements ResolvedOAuthClientResolver {

    private static final String CLIENT_ID = "postman-client";

    private final ResolvedOAuthClient postmanClient;

    public InMemoryPlatformOAuthClientResolver(
            PasswordEncoder passwordEncoder,
            @Value("${takibo.dev.postman-client.secret}") String postmanClientSecret) {
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
