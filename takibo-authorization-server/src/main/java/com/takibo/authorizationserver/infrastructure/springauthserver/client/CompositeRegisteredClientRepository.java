package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.List;

/**
 * Compose plusieurs {@link RegisteredClientRepository} et les interroge dans l'ordre.
 * <p>
 * Ordre attendu : clients PLATFORM (in-memory, bootstrap/signup) d'abord, puis clients SPACE
 * (DB-backed, situés sur une org/space). Le premier qui répond gagne.
 */
public class CompositeRegisteredClientRepository implements RegisteredClientRepository {

    private final List<RegisteredClientRepository> delegates;

    public CompositeRegisteredClientRepository(RegisteredClientRepository... delegates) {
        this.delegates = List.of(delegates);
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Composite registered-client repository is read-only");
    }

    @Override
    public RegisteredClient findById(String id) {
        for (RegisteredClientRepository delegate : delegates) {
            RegisteredClient found = delegate.findById(id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        for (RegisteredClientRepository delegate : delegates) {
            RegisteredClient found = delegate.findByClientId(clientId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
