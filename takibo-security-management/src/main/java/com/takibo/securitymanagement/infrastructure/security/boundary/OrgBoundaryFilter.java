package com.takibo.securitymanagement.infrastructure.security.boundary;

import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.securitymanagement.domain.exception.BoundaryCheckFailedException;
import com.takibo.securitymanagement.domain.exception.BoundaryCheckUnavailableException;
import com.takibo.securitymanagement.sentinel.http.SentinelHttpErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrgBoundaryFilter extends OncePerRequestFilter {

    private final BoundaryMembershipService membershipService;
    private final SentinelHttpErrorWriter sentinelHttpErrorWriter;
    private final PathPatternParser parser = new PathPatternParser();

    // Matche aussi les routes versionnées
    private final List<PathPattern> spaceScopedPatterns = List.of(
            parser.parse("/api/spaces/{spaceId}/**"),
            parser.parse("/api/v1/spaces/{spaceId}/**"),
            // LIGNE CORRIGÉE/SUPPRIMÉE:
            // L'ancien pattern "/api/**/spaces/{spaceId}/**" n'est pas permis par PathPatternParser
            parser.parse("/api/organizations/{orgId}/spaces/{spaceId}/**"),
            parser.parse("/api/v1/organizations/{orgId}/spaces/{spaceId}/**")
    );

    private final List<SkipRule> skipRules = List.of(
            new SkipRule(parser.parse("/error"), null),
            new SkipRule(parser.parse("/favicon.ico"), null),
            new SkipRule(parser.parse("/actuator/**"), null),
            new SkipRule(parser.parse("/swagger-ui/**"), null),
            new SkipRule(parser.parse("/v3/api-docs/**"), null),
            new SkipRule(parser.parse("/api/v1/auth/login"), "POST"),
            new SkipRule(parser.parse("/api/v1/orgs/signup"), "POST")
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        PathContainer path = requestPath(request);
        for (SkipRule r : skipRules) if (r.matches(request, path)) return true;
        return resolveSpaceId(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        UUID spaceId = resolveSpaceId(req);
        if (spaceId != null) {
            // IMPORTANT: ce filtre doit s'exécuter APRÈS le JwtAuthenticationFilter
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            try {
                membershipService.assertActorInSpaceOrg(spaceId, auth);
            } catch (AccessDeniedException | AuthenticationException
                     | SpaceNotFoundException | BoundaryCheckUnavailableException e) {
                // Ce filtre précède ExceptionTranslationFilter et le @ControllerAdvice : sans
                // traduction ici, l'exception remonterait au conteneur. Réponse publique générique.
                reject(e, req, res);
                return;
            } catch (RuntimeException e) {
                // Une panne peut surgir avant que le service n'entre dans ses blocs JDBC (ouverture
                // de la transaction @Transactional) : elle arrive alors brute. Indisponibilité
                // temporaire -> 503 ; tout le reste -> 500 générique.
                reject(isTemporaryOutage(e)
                        ? new BoundaryCheckUnavailableException("Boundary store unavailable", e)
                        : new BoundaryCheckFailedException("Boundary check failed", e), req, res);
                return;
            }
            req.setAttribute("TAKIBO_BOUNDARY_OK", Boolean.TRUE);
        }
        chain.doFilter(req, res);
    }

    private static boolean isTemporaryOutage(RuntimeException e) {
        return e instanceof CannotCreateTransactionException
                || e instanceof TransientDataAccessException
                || e instanceof DataAccessResourceFailureException;
    }

    /** Arrête la chaîne : pas de marqueur boundary, cause conservée dans les logs avec le traceId. */
    private void reject(RuntimeException failure, HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        String traceId = MDC.get("traceId");
        boolean ownTrace = traceId == null || traceId.isBlank();
        if (ownTrace) {
            traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
        }
        try {
            log.warn("Boundary rejected request: traceId={} path={} outcome={}",
                    traceId, req.getRequestURI(), failure.getClass().getSimpleName(), failure);
            sentinelHttpErrorWriter.write(failure, req, res);
        } finally {
            if (ownTrace) {
                MDC.remove("traceId");
            }
        }
    }

    private UUID resolveSpaceId(HttpServletRequest req) {
        PathContainer pc = requestPath(req);
        for (PathPattern p : spaceScopedPatterns) {
            var mi = p.matchAndExtract(pc);
            if (mi != null) {
                String raw = mi.getUriVariables().get("spaceId");
                try { return UUID.fromString(raw); } catch (Exception ignored) { return null; }
            }
        }
        String q = req.getParameter("spaceId");
        if (q != null) { try { return UUID.fromString(q); } catch (Exception ignored) {} }
        return null;
    }

    private PathContainer requestPath(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) uri = uri.substring(ctx.length());
        return PathContainer.parsePath(uri);
    }
}