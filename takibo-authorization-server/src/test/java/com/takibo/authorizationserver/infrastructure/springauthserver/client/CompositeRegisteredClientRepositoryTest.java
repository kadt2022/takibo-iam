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
    void given_platform_client_id_hit_when_find_by_client_id_then_db_is_not_called() {
        when(platform.findByClientId("c")).thenReturn(sample);
        var repo = new CompositeRegisteredClientRepository(platform, db);

        assertThat(repo.findByClientId("c")).isSameAs(sample);
        verify(db, never()).findByClientId(any());
    }

    @Test
    void given_platform_client_id_miss_when_find_by_client_id_then_db_result_is_returned() {
        when(platform.findByClientId("c")).thenReturn(null);
        when(db.findByClientId("c")).thenReturn(sample);
        var repo = new CompositeRegisteredClientRepository(platform, db);

        assertThat(repo.findByClientId("c")).isSameAs(sample);
    }

    @Test
    void given_no_delegate_matches_client_id_when_find_by_client_id_then_returns_null() {
        when(platform.findByClientId("x")).thenReturn(null);
        when(db.findByClientId("x")).thenReturn(null);
        var repo = new CompositeRegisteredClientRepository(platform, db);

        assertThat(repo.findByClientId("x")).isNull();
    }

    @Test
    void given_any_registered_client_when_save_then_throws_read_only_exception() {
        var repo = new CompositeRegisteredClientRepository(platform, db);
        assertThatThrownBy(() -> repo.save(sample)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void given_platform_id_hit_when_find_by_id_then_db_is_not_called() {
        when(platform.findById("1")).thenReturn(sample);
        var repo = new CompositeRegisteredClientRepository(platform, db);

        assertThat(repo.findById("1")).isSameAs(sample);
        verify(db, never()).findById(any());
    }

    @Test
    void given_platform_id_miss_when_find_by_id_then_db_result_is_returned() {
        when(platform.findById("1")).thenReturn(null);
        when(db.findById("1")).thenReturn(sample);
        var repo = new CompositeRegisteredClientRepository(platform, db);

        assertThat(repo.findById("1")).isSameAs(sample);
    }
}
