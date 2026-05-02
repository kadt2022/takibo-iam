package com.takibo.securitymanagement.infrastructure.security;

import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitymanagement.domain.exception.InvalidTokenException;
import com.takibo.securitymanagement.infrastructure.token.JwtValidationException;
import com.takibo.securitymanagement.infrastructure.token.TokenValidatorAdapter;
import com.takibo.securitymanagement.sentinel.http.SentinelHttpErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenValidatorAdapter tokenValidatorAdapter;
    private final SentinelHttpErrorWriter sentinelHttpErrorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String rawToken = authorizationHeader.substring(7).trim();

        try {
            Map<String, Object> claims = tokenValidatorAdapter.validate(rawToken);

            TakiboSecurityContext takiboSecurityContext = TakiboSecurityContextFactory.from(claims, request);

            // FIX 403: authorities doivent être dérivées des CLAIMS (permissions/authorities/scope)
            Collection<GrantedAuthority> authorities = AuthorityFactory.from(claims, takiboSecurityContext);

            String principalName = PrincipalNameFactory.from(claims, takiboSecurityContext);

            log.debug("Authorities resolved: {}", authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList());

            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(rawToken, principalName, takiboSecurityContext, authorities);

            var securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            log.debug("JWT validated principal={} subjectId={} orgId={} spaceId={}",
                    principalName,
                    takiboSecurityContext.subject().subjectId(),
                    takiboSecurityContext.tenant().organizationId(),
                    takiboSecurityContext.tenant().spaceId());

            chain.doFilter(request, response);

        } catch (JwtValidationException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            sentinelHttpErrorWriter.write(new InvalidTokenException("Token invalide ou expiré", e), request, response);
        }
    }
}
