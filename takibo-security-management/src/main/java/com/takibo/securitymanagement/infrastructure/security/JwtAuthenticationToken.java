package com.takibo.securitymanagement.infrastructure.security;

import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.spi.TakiboSecurityContextCarrier;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Objects;

public final class JwtAuthenticationToken extends AbstractAuthenticationToken implements TakiboSecurityContextCarrier {

    private final String token;
    private final String principal;
    private final TakiboSecurityContext securityContext;

    public JwtAuthenticationToken(String token,
                                  String principal,
                                  TakiboSecurityContext securityContext,
                                  Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.token = Objects.requireNonNull(token, "token");
        this.principal = (principal == null || principal.isBlank()) ? "anonymous" : principal;
        this.securityContext = Objects.requireNonNull(securityContext, "securityContext");
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal;
    }

    @Override
    public TakiboSecurityContext getSecurityContext() {
        return securityContext;
    }
}
