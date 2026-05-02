package com.takibo.securitymanagement.infrastructure.audit;

import com.takibo.audit.core.TakiboAuditUserContextHolder;
import com.takibo.audit.spi.TakiboAuditUserContext;
import com.takibo.securitycontext.exception.TakiboSecurityContextNotAvailableException;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Deprecated
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class TakibuAuditUserContextFilter extends OncePerRequestFilter {

    private final CurrentTakiboSecurityContextProvider currentTakiboSecurityContextProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        TakiboAuditUserContextHolder.setContext(new TakiboAuditUserContext() {
            @Override
            public String getUserId() {
                return resolveActorId();
            }
        });

        try {
            filterChain.doFilter(request, response);
        } finally {
            TakiboAuditUserContextHolder.clear();
        }
    }

    private String resolveActorId() {
        TakiboSecurityContext ctx = getOptionalContext();
        if (ctx != null && ctx.subject() != null) {
            String actorId = ctx.subject().subjectId();
            if (actorId != null && !actorId.isBlank()) {
                return actorId;
            }
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof String principalName) {
            return principalName;
        }

        return authentication.getName();
    }

    private TakiboSecurityContext getOptionalContext() {
        try {
            return currentTakiboSecurityContextProvider.current();
        } catch (TakiboSecurityContextNotAvailableException e) {
            return null;
        }
    }
}
