package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeRegisteredClientRepositoryTest {

    @Mock private RegisteredClientRepository platform;
    @Mock private RegisteredClientRepository db;

    private final RegisteredClient sample = RegisteredClient.withId("1")
            .clientId("c")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .build();

    @Test
    void platform_hit_short_circuits_db() {
        when(platform.findByClientId("c")).thenReturn(sample);
        var repo = new CompositeRegisteredClientRepository(platform, db);

        assertThat(repo.findByClientId("c")).isSameAs(sample);
        verify(db, never()).findByClientId(any());
    }

    @Test
    void falls_through_to_db_when_platform_misses() {
        when(platform.findByClientId("c")).thenReturn(null);
        when(db.findByClientId("c")).thenReturn(sample);
        var repo = new CompositeRegisteredClientRepository(platform, db);

        assertThat(repo.findByClientId("c")).isSameAs(sample);
    }

    @Test
    void returns_null_when_no_delegate_matches() {
        when(platform.findByClientId("x")).thenReturn(null);
        when(db.findByClientId("x")).thenReturn(null);
        var repo = new CompositeRegisteredClientRepository(platform, db);

        assertThat(repo.findByClientId("x")).isNull();
    }

    @Test
    void save_is_read_only() {
        var repo = new CompositeRegisteredClientRepository(platform, db);
        assertThatThrownBy(() -> repo.save(sample)).isInstanceOf(UnsupportedOperationException.class);
    }
}
