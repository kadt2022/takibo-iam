package com.takibo.securitycontextspring.provider;

import com.takibo.securitycontext.exception.TakiboSecurityContextNotAvailableException;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import com.takibo.securitycontext.spi.TakiboSecurityContextCarrier;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SpringCurrentTakiboSecurityContextProvider implements CurrentTakiboSecurityContextProvider {

    @Override
    public TakiboSecurityContext current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new TakiboSecurityContextNotAvailableException("No authenticated TakiboSecurityContext available");
        }

        if (auth instanceof TakiboSecurityContextCarrier carrier) {
            return carrier.getSecurityContext();
        }

        throw new TakiboSecurityContextNotAvailableException(
                "Authentication does not carry TakiboSecurityContext. type=" + auth.getClass().getName()
        );
    }
}
