package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TakiboRegisteredClientRepositoryConfigTest {

    @Test
    void given_platform_and_takibo_repositories_when_registered_client_repository_is_created_then_platform_is_checked_first() {
        RegisteredClient platformClient = registeredClient("platform-id", "postman-client");
        InMemoryRegisteredClientRepository platform =
                new InMemoryRegisteredClientRepository(platformClient);
        TakiboRegisteredClientRepository takibo = mock(TakiboRegisteredClientRepository.class);
        RegisteredClientRepository repository =
                new TakiboRegisteredClientRepositoryConfig().registeredClientRepository(platform, takibo);

        assertThat(repository).isInstanceOf(CompositeRegisteredClientRepository.class);
        assertThat(repository.findByClientId("postman-client")).isSameAs(platformClient);
        verify(takibo, never()).findByClientId("postman-client");
    }

    @Test
    void given_platform_miss_when_registered_client_repository_searches_then_takibo_repository_is_used() {
        InMemoryRegisteredClientRepository platform =
                new InMemoryRegisteredClientRepository(registeredClient("platform-id", "postman-client"));
        TakiboRegisteredClientRepository takibo = mock(TakiboRegisteredClientRepository.class);
        RegisteredClient dbClient = registeredClient("db-id", "space-client");
        when(takibo.findByClientId("space-client")).thenReturn(dbClient);
        RegisteredClientRepository repository =
                new TakiboRegisteredClientRepositoryConfig().registeredClientRepository(platform, takibo);

        assertThat(repository.findByClientId("space-client")).isSameAs(dbClient);
        verify(takibo).findByClientId("space-client");
    }

    private RegisteredClient registeredClient(String id, String clientId) {
        return RegisteredClient.withId(id)
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
    }
}
