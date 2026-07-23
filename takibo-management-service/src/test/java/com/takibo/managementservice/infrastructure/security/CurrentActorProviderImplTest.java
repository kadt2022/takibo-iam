package com.takibo.managementservice.infrastructure.security;

import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.managementservice.domain.model.ActorSource;
import com.takibo.securitycontext.model.AuthenticationMethod;
import com.takibo.securitycontext.model.ContextAttributeStore;
import com.takibo.securitycontext.model.SubjectIdentity;
import com.takibo.securitycontext.model.SubjectNature;
import com.takibo.securitycontext.model.StandardAttributeKeys;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.model.TemporalContext;
import com.takibo.securitycontext.model.TenantScope;
import com.takibo.securitycontext.spi.TakiboSecurityContextCarrier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentActorProviderImplTest {

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
    void currentUserId_withoutAuthentication_failsClosed() {
        CurrentActorProviderImpl provider = provider();

        assertThatThrownBy(provider::currentUserId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No authenticated actor");
    }

    @Test
    void currentUserId_withTakiboPrincipal_returnsPrincipalUserId() {
        TakiboPrincipal principal = new TakiboPrincipal(
                "sub", "jdoe", USER_ID, ACCOUNT_ID, null, null, List.of(), List.of());
        authenticate(principal);

        assertThat(provider().currentUserId()).isEqualTo(USER_ID);
    }

    @Test
    void currentUserId_withUnknownPrincipal_failsClosed() {
        authenticate("  " + USER_ID + "  ");
        CurrentActorProviderImpl provider = provider();

        assertThatThrownBy(provider::currentUserId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to determine authenticated actor source");
    }

    @Test
    void currentUserId_withInvalidStringPrincipal_failsClosed() {
        authenticate("not-a-uuid");
        CurrentActorProviderImpl provider = provider();

        assertThatThrownBy(provider::currentUserId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to determine authenticated actor source");
    }

    @Test
    void currentUserId_withJwt_uses_the_first_valid_supported_claim() {
        Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-20T01:00:00Z"),
                java.util.Map.of("alg", "none"),
                java.util.Map.of(
                        "subject_type", "HUMAN",
                        "user_id", "invalid",
                        "userId", USER_ID.toString(),
                        "sub", "ignored"));
        authenticate(jwt);

        assertThat(provider().currentUserId()).isEqualTo(USER_ID);
    }

    @Test
    void currentAccountId_delegatesToCurrentAccountContext() {
        authenticate(humanPrincipal());
        when(currentAccountContext.requireCurrentAccountId()).thenReturn(ACCOUNT_ID);

        assertThat(provider().currentAccountId()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void currentAccountId_withoutAuthentication_failsClosed() {
        CurrentActorProviderImpl provider = provider();

        assertThatThrownBy(provider::currentAccountId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No authenticated actor");
    }

    @Test
    void source_withoutAuthentication_failsClosed() {
        CurrentActorProviderImpl provider = provider();

        assertThatThrownBy(provider::source)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No authenticated actor");
    }

    @Test
    void source_withAnonymousAuthentication_failsClosed() {
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.AnonymousAuthenticationToken(
                        "key", "anonymous",
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        CurrentActorProviderImpl provider = provider();

        assertThatThrownBy(provider::source)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No authenticated actor");
    }

    @Test
    void source_withHumanPrincipalCarryingAccount_isHuman() {
        TakiboPrincipal principal = new TakiboPrincipal(
                "sub", "jdoe", USER_ID, ACCOUNT_ID, null, null, List.of(), List.of());
        authenticate(principal);

        assertThat(provider().source()).isEqualTo(ActorSource.HUMAN);
    }

    @Test
    void source_withJwtCarryingAccountClaim_isHuman() {
        Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-20T01:00:00Z"),
                java.util.Map.of("alg", "none"),
                java.util.Map.of(
                        "subject_type", "HUMAN",
                        "account_id", ACCOUNT_ID.toString(),
                        "user_id", USER_ID.toString(),
                        "sub", "founder"));
        authenticate(jwt);

        assertThat(provider().source()).isEqualTo(ActorSource.HUMAN);
    }

    @Test
    void source_withMachineJwtWithoutAccount_isServiceAccount() {
        Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-20T01:00:00Z"),
                java.util.Map.of("alg", "none"),
                java.util.Map.of(
                        "subject_type", "CLIENT_APP",
                        "client_id", "tms-cli",
                        "sub", "service-tms"));
        authenticate(jwt);

        assertThat(provider().source()).isEqualTo(ActorSource.SERVICE_ACCOUNT);
    }

    @Test
    void source_withApplicationHumanAuthenticationToken_isHuman() {
        authenticateWithApplicationToken(SubjectNature.HUMAN);

        assertThat(provider().source()).isEqualTo(ActorSource.HUMAN);
    }

    @Test
    void source_withApplicationServiceAuthenticationToken_isServiceAccount() {
        authenticateWithApplicationToken(SubjectNature.SERVICE);

        assertThat(provider().source()).isEqualTo(ActorSource.SERVICE_ACCOUNT);
    }

    @Test
    void currentUserId_withApplicationHumanAuthenticationToken_usesContextUserId() {
        authenticateWithApplicationToken(SubjectNature.HUMAN);

        assertThat(provider().currentUserId()).isEqualTo(USER_ID);
    }

    @Test
    void currentUserId_withServiceAccount_failsClosed() {
        authenticateWithApplicationToken(SubjectNature.SERVICE);
        CurrentActorProviderImpl provider = provider();

        assertThatThrownBy(provider::currentUserId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Current actor is not a human user");
    }

    @Test
    void source_withAmbiguousLegacyJwt_failsClosed() {
        Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-20T01:00:00Z"),
                Map.of("alg", "none"),
                Map.of("sub", "unknown"));
        authenticate(jwt);
        CurrentActorProviderImpl provider = provider();

        assertThatThrownBy(provider::source)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to determine authenticated actor source");
    }

    @Test
    void source_withAuthenticatedSystemContext_failsClosed() {
        authenticateWithApplicationToken(SubjectNature.SYSTEM);
        CurrentActorProviderImpl provider = provider();

        assertThatThrownBy(provider::source)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SYSTEM actor is not allowed through an authenticated request");
    }

    private void authenticateWithApplicationToken(SubjectNature nature) {
        TakiboSecurityContext context = TakiboSecurityContext.builder()
                .subject(new SubjectIdentity(
                        USER_ID.toString(), nature, Set.of(), AuthenticationMethod.OAUTH2))
                .tenant(new TenantScope(null, null))
                .temporal(new TemporalContext(
                        Instant.parse("2026-07-20T00:00:00Z"), null, "test", 1))
                .attributes(new ContextAttributeStore(
                        nature == SubjectNature.HUMAN
                                ? Map.of(StandardAttributeKeys.USER_ID, USER_ID)
                                : Map.of()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new ContextCarryingAuthentication(USER_ID.toString(), context));
    }

    private static final class ContextCarryingAuthentication extends AbstractAuthenticationToken
            implements TakiboSecurityContextCarrier {
        private final String principal;
        private final TakiboSecurityContext context;

        private ContextCarryingAuthentication(String principal, TakiboSecurityContext context) {
            super(List.of());
            this.principal = principal;
            this.context = context;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return "token";
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }

        @Override
        public TakiboSecurityContext getSecurityContext() {
            return context;
        }
    }

    private CurrentActorProviderImpl provider() {
        return new CurrentActorProviderImpl(currentAccountContext);
    }

    private TakiboPrincipal humanPrincipal() {
        return new TakiboPrincipal(
                "sub", "jdoe", USER_ID, ACCOUNT_ID, null, null, List.of(), List.of());
    }

    private void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
