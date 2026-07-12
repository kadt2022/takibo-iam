package com.takibo.managementservice.application.security;

import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentActorProviderImplTest {

    private static final UUID SYSTEM_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Mock private CurrentAccountContextCase currentAccountContext;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserId_withoutAuthentication_returnsSystemActor() {
        CurrentActorProviderImpl provider = new CurrentActorProviderImpl(currentAccountContext);

        assertThat(provider.currentUserId()).isEqualTo(SYSTEM_ACTOR_ID);
    }

    @Test
    void currentUserId_withTakiboPrincipal_returnsPrincipalUserId() {
        TakiboPrincipal principal = new TakiboPrincipal(
                "sub", "jdoe", USER_ID, ACCOUNT_ID, null, null, List.of(), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a"));
        CurrentActorProviderImpl provider = new CurrentActorProviderImpl(currentAccountContext);

        assertThat(provider.currentUserId()).isEqualTo(USER_ID);
    }

    @Test
    void currentUserId_withInvalidStringPrincipal_returnsSystemActor() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("not-a-uuid", "n/a"));
        CurrentActorProviderImpl provider = new CurrentActorProviderImpl(currentAccountContext);

        assertThat(provider.currentUserId()).isEqualTo(SYSTEM_ACTOR_ID);
    }

    @Test
    void currentAccountId_delegatesToCurrentAccountContext() {
        when(currentAccountContext.requireCurrentAccountId()).thenReturn(ACCOUNT_ID);
        CurrentActorProviderImpl provider = new CurrentActorProviderImpl(currentAccountContext);

        assertThat(provider.currentAccountId()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void source_isSystem() {
        CurrentActorProviderImpl provider = new CurrentActorProviderImpl(currentAccountContext);

        assertThat(provider.source()).isEqualTo(ActorSource.SYSTEM);
    }
}
