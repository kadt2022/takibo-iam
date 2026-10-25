package com.takibo.securitycontextspring;

import com.takibo.securitycontext.exception.TakiboSecurityContextNotAvailableException;
import com.takibo.securitycontext.model.AuthenticationMethod;
import com.takibo.securitycontext.model.SubjectIdentity;
import com.takibo.securitycontext.model.SubjectNature;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.model.TemporalContext;
import com.takibo.securitycontext.model.TenantScope;
import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import com.takibo.securitycontext.spi.TakiboSecurityContextCarrier;
import com.takibo.securitycontextspring.config.TakiboSecurityContextSpringConfig;
import com.takibo.securitycontextspring.provider.SpringCurrentTakiboSecurityContextProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringCurrentTakiboSecurityContextProviderTest {

    private static final TakiboSecurityContext CONTEXT = TakiboSecurityContext.builder()
            .subject(new SubjectIdentity("acc-1", SubjectNature.HUMAN, Set.of(), AuthenticationMethod.OIDC))
            .tenant(new TenantScope(null, null))
            .temporal(new TemporalContext(Instant.parse("2026-10-25T10:00:00Z"), null, null, 1))
            .build();

    private final SpringCurrentTakiboSecurityContextProvider provider = new SpringCurrentTakiboSecurityContextProvider();

    /** Authentification qui porte un contexte Takibo, comme celle produite par security-management. */
    static final class CarrierAuthentication extends AbstractAuthenticationToken implements TakiboSecurityContextCarrier {
        private final TakiboSecurityContext context;

        CarrierAuthentication(TakiboSecurityContext context, boolean authenticated) {
            super(AuthorityUtils.NO_AUTHORITIES);
            this.context = context;
            setAuthenticated(authenticated);
        }

        @Override public Object getCredentials() { return null; }
        @Override public Object getPrincipal() { return "acc-1"; }
        @Override public TakiboSecurityContext getSecurityContext() { return context; }
    }

    private static void authenticate(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedCarrier_exposesItsTakiboContext() {
        authenticate(new CarrierAuthentication(CONTEXT, true));

        assertThat(provider.current()).isSameAs(CONTEXT);
    }

    @Test
    void noAuthentication_isRejected() {
        assertThatThrownBy(provider::current)
                .isInstanceOf(TakiboSecurityContextNotAvailableException.class)
                .hasMessage("No authenticated TakiboSecurityContext available");
    }

    @Test
    void unauthenticatedCarrier_isRejected() {
        authenticate(new CarrierAuthentication(CONTEXT, false));

        assertThatThrownBy(provider::current)
                .isInstanceOf(TakiboSecurityContextNotAvailableException.class)
                .hasMessage("No authenticated TakiboSecurityContext available");
    }

    @Test
    void anonymousUser_isRejected() {
        authenticate(new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(provider::current)
                .isInstanceOf(TakiboSecurityContextNotAvailableException.class)
                .hasMessage("No authenticated TakiboSecurityContext available");
    }

    @Test
    void authenticationWithoutTakiboContext_isRejectedWithItsType() {
        authenticate(UsernamePasswordAuthenticationToken.authenticated("alice", "n/a", AuthorityUtils.NO_AUTHORITIES));

        assertThatThrownBy(provider::current)
                .isInstanceOf(TakiboSecurityContextNotAvailableException.class)
                .hasMessage("Authentication does not carry TakiboSecurityContext. type="
                        + UsernamePasswordAuthenticationToken.class.getName());
    }

    @Test
    void springConfiguration_exposesTheProvider() {
        new ApplicationContextRunner()
                .withUserConfiguration(TakiboSecurityContextSpringConfig.class)
                .run(context -> assertThat(context).getBean(CurrentTakiboSecurityContextProvider.class)
                        .isInstanceOf(SpringCurrentTakiboSecurityContextProvider.class));
    }
}
