package com.takibo.securitymanagement.infrastructure.adp;

import com.takibo.securitymanagement.domain.model.Resource;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.UUID;

/**
 * Traduit le contrat d'URL de l'API en ressource évaluable par la politique. Seul
 * {@code /api/v1/orgs/{orgId}/...} désigne une organisation, et seulement si {@code orgId}
 * est un UUID (RBAC-06 : {@code token.org_id == orgId}). Signup, {@code /me}, routes lisibles
 * en codes et autres chemins n'en désignent aucune ; leur frontière reste aux règles de
 * surface et aux gardes applicatives. Le space n'est pas lu.
 */
final class PolicyResourceResolver {

    private static final PathPattern ORGANIZATION_ROUTE =
            PathPatternParser.defaultInstance.parse("/api/v1/orgs/{orgId}/**");

    private PolicyResourceResolver() {
    }

    static Resource resolve(String path) {
        return new Resource(path, organizationIdOf(path), null);
    }

    private static String organizationIdOf(String path) {
        if (path == null) {
            return null;
        }
        PathPattern.PathMatchInfo match = ORGANIZATION_ROUTE.matchAndExtract(PathContainer.parsePath(path));
        if (match == null) {
            return null;
        }
        try {
            return UUID.fromString(match.getUriVariables().get("orgId")).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
