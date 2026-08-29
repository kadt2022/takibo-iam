package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;

import java.util.List;
import java.util.Optional;

/**
 * Compose plusieurs {@link ResolvedOAuthClientResolver} et les interroge dans l'ordre
 * (TAS-GRANTS-01).
 * <p>
 * Ordre attendu : source PLATFORM (in-memory, dev et test) d'abord si présente, puis source
 * TMS (DB-backed). Le premier résultat présent gagne. C'est désormais l'unique chemin de
 * résolution de {@code RegisteredClientRepository} au sens large : {@code
 * TakiboRegisteredClientRepository} le consomme directement, sans composer de son côté avec
 * un second {@code RegisteredClientRepository} PLATFORM séparé comme le faisait l'ancien
 * {@code CompositeRegisteredClientRepository} (retiré).
 */
public class CompositeResolvedOAuthClientResolver implements ResolvedOAuthClientResolver {

    private final List<ResolvedOAuthClientResolver> delegates;

    public CompositeResolvedOAuthClientResolver(ResolvedOAuthClientResolver... delegates) {
        this.delegates = List.of(delegates);
    }

    @Override
    public Optional<ResolvedOAuthClient> resolve(String clientId) {
        for (ResolvedOAuthClientResolver delegate : delegates) {
            Optional<ResolvedOAuthClient> found = delegate.resolve(clientId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
