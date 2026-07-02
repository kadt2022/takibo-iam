package com.takibo.securitymanagement.infrastructure.security.boundary;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

@Component
@RequiredArgsConstructor
public class OrgBoundaryFilter extends OncePerRequestFilter {

    private final BoundaryMembershipService membershipService;
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
            new SkipRule(parser.parse("/api/public/**"), null),
            new SkipRule(parser.parse("/api/auth/**"), null),
            new SkipRule(parser.parse("/api/v1/auth/login"), "POST"),
            // signups publics éventuels
            new SkipRule(parser.parse("/api/spaces/signup"), "POST"),
            new SkipRule(parser.parse("/api/v1/spaces/signup"), "POST"),
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
            membershipService.assertActorInSpaceOrg(spaceId, auth);
            req.setAttribute("TAKIBO_BOUNDARY_OK", Boolean.TRUE);
        }
        chain.doFilter(req, res);
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