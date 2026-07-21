package com.takibo.managementservice.infrastructure.security;

import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.managementservice.domain.model.ActorSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
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

    @Mock
    private CurrentAccountContextCase currentAccountContext;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserId_withoutAuthentication_returnsSystemActor() {
        assertThat(provider().currentUserId()).isEqualTo(SYSTEM_ACTOR_ID);
    }

    @Test
    void currentUserId_withTakiboPrincipal_returnsPrincipalUserId() {
        TakiboPrincipal principal = new TakiboPrincipal(
                "sub", "jdoe", USER_ID, ACCOUNT_ID, null, null, List.of(), List.of());
        authenticate(principal);

        assertThat(provider().currentUserId()).isEqualTo(USER_ID);
    }

    @Test
    void currentUserId_withUuidStringPrincipal_returnsItsValue() {
        authenticate("  " + USER_ID + "  ");

        assertThat(provider().currentUserId()).isEqualTo(USER_ID);
    }

    @Test
    void currentUserId_withInvalidStringPrincipal_returnsSystemActor() {
        authenticate("not-a-uuid");

        assertThat(provider().currentUserId()).isEqualTo(SYSTEM_ACTOR_ID);
    }

    @Test
    void currentUserId_withJwt_uses_the_first_valid_supported_claim() {
        Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-20T01:00:00Z"),
                java.util.Map.of("alg", "none"),
                java.util.Map.of("user_id", "invalid", "userId", USER_ID.toString(), "sub", "ignored"));
        authenticate(jwt);

        assertThat(provider().currentUserId()).isEqualTo(USER_ID);
    }

    @Test
    void currentAccountId_delegatesToCurrentAccountContext() {
        when(currentAccountContext.requireCurrentAccountId()).thenReturn(ACCOUNT_ID);

        assertThat(provider().currentAccountId()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void source_isSystem() {
        assertThat(provider().source()).isEqualTo(ActorSource.SYSTEM);
    }

    private CurrentActorProviderImpl provider() {
        return new CurrentActorProviderImpl(currentAccountContext);
    }

    private void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a"));
    }
}
