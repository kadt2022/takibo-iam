package com.takibo.securitymanagement.infrastructure.security;

import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;
import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Seule porte d'entrée des permissions d'un token dans TSM : les autorités Spring, l'ADP
 * ({@code Subject.permissions}) et les politiques ne voient que les permissions retenues ici.
 * <p>
 * Une permission n'est retenue que si son code appartient au catalogue
 * ({@link TechnicalPermission}) et que son plan est exactement celui du niveau du token,
 * établi par {@link TokenLevel}. C'est la règle que l'émetteur impose déjà
 * ({@code HumanTokenRequest}) : la vérifier à la réception protège aussi d'un token signé par
 * erreur. Les permissions ORGANIZATION utilisables dans un space n'y font pas exception : la
 * table de projection de l'ADR les réécrit en permissions SPACE à l'émission.
 * <p>
 * Le claim {@code authorities}, absent du contrat de token TAS, est toujours retiré : une
 * chaîne arbitraire n'y deviendrait plus une autorité. Les scopes OAuth2 ({@code scope}) ne
 * sont pas des permissions RBAC et ne sont pas touchés.
 */
public final class TokenPermissionFilter {

    static final String PERMISSIONS = "permissions";
    static final String AUTHORITIES = "authorities";

    private static final Logger log = LoggerFactory.getLogger(TokenPermissionFilter.class);

    private TokenPermissionFilter() {
    }

    /** Les claims, avec des permissions réduites à celles admises par le niveau du token. */
    public static Map<String, Object> sanitize(Map<String, Object> claims) {
        if (claims == null || !(claims.containsKey(PERMISSIONS) || claims.containsKey(AUTHORITIES))) {
            return claims;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>(claims);
        if (sanitized.remove(AUTHORITIES) != null) {
            log.warn("Token : claim {} hors contrat retiré pour le sujet {}", AUTHORITIES, claims.get("sub"));
        }
        if (claims.containsKey(PERMISSIONS)) {
            TokenLevel level = TokenLevel.of(claims);
            List<String> kept = new ArrayList<>();
            List<String> dropped = new ArrayList<>();
            for (Object value : TokenRoleFilter.claimValues(claims.get(PERMISSIONS))) {
                String code = value == null ? null : value.toString();
                if (isAdmitted(code, level)) {
                    kept.add(code);
                } else {
                    dropped.add(String.valueOf(code));
                }
            }
            if (!dropped.isEmpty()) {
                log.warn("Token : permissions écartées pour le sujet {} (niveau {}) : {}",
                        claims.get("sub"), level, dropped);
            }
            if (kept.isEmpty()) {
                sanitized.remove(PERMISSIONS);
            } else {
                sanitized.put(PERMISSIONS, List.copyOf(kept));
            }
        }
        return Map.copyOf(sanitized);
    }

    static boolean isAdmitted(String code, TokenLevel level) {
        if (code == null || code.isBlank()) {
            return false;
        }
        Optional<AuthorityPlan> plan = level.permissionPlan();
        return plan.isPresent() && TechnicalPermission.fromCode(code)
                .map(permission -> permission.plan() == plan.get())
                .orElse(false);
    }
}
