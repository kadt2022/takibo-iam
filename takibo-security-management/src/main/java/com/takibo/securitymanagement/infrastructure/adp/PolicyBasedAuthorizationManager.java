package com.takibo.securitymanagement.infrastructure.adp;

import com.takibo.adp.api.AdaptiveDecisionPort;
import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.api.DecisionResponse;
import com.takibo.adp.spring.adapter.RequestVelocityTracker;
import com.takibo.securitycontext.exception.TakiboSecurityContextNotAvailableException;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyBasedAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final AdaptiveDecisionPort adaptiveDecisionPort;
    private final RequestVelocityTracker velocityTracker;
    private final CurrentTakiboSecurityContextProvider currentTakiboSecurityContextProvider;

    @Override
    public AuthorizationDecision authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext context
    ) {
        Authentication authentication = authenticationSupplier.get();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            log.debug("ADP: anonymous or unauthenticated request, denying access");
            return new AuthorizationDecision(false);
        }

        TakiboSecurityContext ctx;
        try {
            ctx = currentTakiboSecurityContextProvider.current();
        } catch (TakiboSecurityContextNotAvailableException e) {
            log.warn("ADP: missing TakiboSecurityContext, denying access: {}", e.getMessage());
            return new AuthorizationDecision(false);
        }

        HttpServletRequest request = context.getRequest();

        String subjectId = ctx.subject().subjectId();
        String organizationId = ctx.tenant() != null ? ctx.tenant().organizationId() : null;
        String spaceId = ctx.tenant() != null ? ctx.tenant().spaceId() : null;

        velocityTracker.recordRequest(subjectId);

        Set<String> roles = extractRoles(authentication, ctx);
        Set<String> permissions = extractPermissions(authentication);

        RequestVelocityTracker.VelocitySnapshot velocity = velocityTracker.getVelocity(subjectId);

        DecisionRequest decisionRequest = new DecisionRequest(
                subjectId,
                organizationId,
                spaceId,
                roles,
                permissions,
                request.getRequestURI(),
                request.getMethod(),
                Instant.now(),
                ctx.transport() != null ? ctx.transport().ipAddress() : request.getRemoteAddr(),
                extractDeviceFingerprint(request),
                request.getHeader("User-Agent"),
                null, // country
                null, // city
                null, // isVpn
                detectProxy(request),
                null, // isTor
                request.getSession(false) != null ? request.getSession(false).getId() : null,
                velocity.last10s(),
                velocity.last60s(),
                15,
                "1.0",
                Map.of()
        );

        try {
            DecisionResponse response = adaptiveDecisionPort.evaluate(decisionRequest);

            log.info("ADP Decision: user={} path={} decision={} risk={} confidence={} explanation={}",
                    subjectId,
                    request.getRequestURI(),
                    response.decision(),
                    String.format("%.1f", response.riskScore()),
                    String.format("%.2f", response.confidence()),
                    response.explanation());

            if (response.requiresChallenge()) {
                log.warn("CHALLENGE required for user={} path={} - Step-up MFA needed",
                        subjectId, request.getRequestURI());
                request.setAttribute("adp.challenge.required", true);
                request.setAttribute("adp.decision.response", response);
            }

            return new AuthorizationDecision(response.isAllowed());

        } catch (Exception e) {
            log.error("ADP evaluation failed for user={} path={}, denying access",
                    subjectId, request.getRequestURI(), e);
            return new AuthorizationDecision(false);
        }
    }

    private Set<String> extractRoles(Authentication authentication, TakiboSecurityContext ctx) {
        Set<String> roles = new LinkedHashSet<>();

        // Depuis TakiboSecurityContext (sans préfixe ROLE_)
        if (ctx != null && ctx.subject() != null && ctx.subject().declaredRoles() != null) {
            for (String role : ctx.subject().declaredRoles()) {
                if (role == null || role.isBlank()) continue;
                roles.add(stripRolePrefix(role));
            }
        }

        // Depuis Spring Security (avec préfixe ROLE_)
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String auth = authority.getAuthority();
            if (auth != null && !auth.isBlank() && auth.startsWith("ROLE_")) {
                roles.add(stripRolePrefix(auth));
            }
        }

        return Set.copyOf(roles);
    }

    private Set<String> extractPermissions(Authentication authentication) {
        Set<String> permissions = new LinkedHashSet<>();

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String auth = authority.getAuthority();
            if (auth == null || auth.isBlank()) continue;
            if (auth.startsWith("ROLE_")) continue;
            if (auth.startsWith("SCOPE_")) continue;

            permissions.add(auth);
        }

        return Set.copyOf(permissions);
    }

    private String stripRolePrefix(String role) {
        if (role == null) return null;
        return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
    }

    private String extractDeviceFingerprint(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        String accept = request.getHeader("Accept");
        String lang = request.getHeader("Accept-Language");

        if (ua == null && accept == null) {
            return "UNKNOWN";
        }

        String base = (ua != null ? ua : "") + "|" + (accept != null ? accept : "") + "|" + (lang != null ? lang : "");
        return Integer.toHexString(base.hashCode());
    }

    private Boolean detectProxy(HttpServletRequest request) {
        String via = request.getHeader("Via");
        String forwarded = request.getHeader("X-Forwarded-For");

        if (via != null) return true;
        if (forwarded != null && forwarded.split(",").length > 1) return true;

        return null;
    }
}
