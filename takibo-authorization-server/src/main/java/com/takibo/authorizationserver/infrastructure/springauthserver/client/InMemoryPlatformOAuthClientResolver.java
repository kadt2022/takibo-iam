package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ClientType;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Source PLATFORM in-memory de développement : ne résout que {@code postman-client}
 * (TAS-GRANTS-01).
 * <p>
 * Construite par {@link ResolvedOAuthClientResolverConfig}, réservée aux profils {@code dev},
 * {@code test} et {@code ci} — en dehors, cette source est absente du contexte et seule la
 * source TMS ({@link JpaResolvedOAuthClientResolver}) reste active, conformément au périmètre
 * du récit.
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

    /**
     * Identifiant technique stable de {@code postman-client}, dérivé déterministe de son
     * {@code client_id} plutôt que tiré au hasard à chaque démarrage (TAS-GRANTS-02).
     * <p>
     * {@code OAuth2AuthorizationService} persiste {@code RegisteredClient.getId()}, pas le
     * {@code client_id} public, et le relit après un redémarrage pour reconstruire une
     * autorisation ({@code registeredClientRepository.findById(...)}). Un
     * {@code UUID.randomUUID()} tiré dans ce constructeur — recréé à chaque démarrage de
     * l'application, ce bean n'étant construit qu'une fois par instance — romprait ce lien
     * dès le premier redémarrage : la ligne persistée porterait l'ancien identifiant, que
     * plus aucune instance ne reconnaîtrait.
     */
    static final UUID REGISTERED_CLIENT_ID =
            UUID.nameUUIDFromBytes(CLIENT_ID.getBytes(StandardCharsets.UTF_8));

    private final ResolvedOAuthClient postmanClient;

    public InMemoryPlatformOAuthClientResolver(
            PasswordEncoder passwordEncoder,
            String postmanClientSecret) {
        this.postmanClient = new ResolvedOAuthClient(
                REGISTERED_CLIENT_ID.toString(),
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
