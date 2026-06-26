package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Câble le {@link RegisteredClientRepository} effectif de TAS : un composite qui interroge
 * d'abord les clients PLATFORM (in-memory) puis les clients SPACE (DB-backed `oauth2_clients`).
 * Marqué {@link Primary} pour que Spring Authorization Server le résolve sans ambiguïté.
 */
@Configuration
public class TakiboRegisteredClientRepositoryConfig {

    @Bean
    @Primary
    public RegisteredClientRepository registeredClientRepository(
            InMemoryRegisteredClientRepository platformRegisteredClientRepository,
            TakiboRegisteredClientRepository takiboRegisteredClientRepository) {
        return new CompositeRegisteredClientRepository(
                platformRegisteredClientRepository,
                takiboRegisteredClientRepository);
    }
}
