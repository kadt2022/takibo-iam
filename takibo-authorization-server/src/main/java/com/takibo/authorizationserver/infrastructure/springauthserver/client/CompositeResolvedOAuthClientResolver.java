package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;

import java.util.List;
import java.util.Optional;

/**
 * Compose plusieurs {@link ResolvedOAuthClientResolver} et les interroge dans l'ordre
 * (TAS-GRANTS-01).
 * <p>
 * Ordre attendu : source PLATFORM (in-memory, dev uniquement) d'abord si présente, puis
 * source TMS (DB-backed). Le premier résultat présent gagne ; miroir de
 * {@link CompositeRegisteredClientRepository}, qui compose de la même façon côté
 * {@code RegisteredClientRepository}.
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
