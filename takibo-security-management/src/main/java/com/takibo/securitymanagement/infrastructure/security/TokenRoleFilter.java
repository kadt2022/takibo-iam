package com.takibo.securitymanagement.infrastructure.security;

import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.domain.catalogrbac.TenantRoleCodePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Seule porte d'entrée des rôles d'un token dans TSM : le contexte de sécurité, les autorités
 * Spring, l'ADP et les contrôles d'appartenance ne voient que les rôles qu'elle retient.
 * <p>
 * Un rôle n'est retenu que s'il a un sens au niveau du token, établi par les claims signés
 * par TAS ({@code takibo_scope_level}, {@code org_id}, {@code space_id}) :
 * <ul>
 *   <li>code du catalogue technique : son plan doit être admis par ce niveau — ORGANIZATION et
 *       SPACE pour un token SPACE, ORGANIZATION pour un token ORG, PLATFORM pour un token
 *       PLATFORM (sans organisation ni space) ; un rôle libre-service ne l'est jamais ;</li>
 *   <li>code de tenant (GOVERNANCE) : seulement dans un token SPACE, et jamais un code réservé
 *       ({@link TenantRoleCodePolicy}) — ce qui écarte toute forme préfixée {@code ROLE_} ;</li>
 *   <li>tout le reste est écarté, y compris les variantes de casse ou d'espaces.</li>
 * </ul>
 * Ces règles reproduisent celles de l'émetteur : elles protègent aussi un token signé avant
 * une correction de l'émetteur, tant qu'il reste valide.
 */
public final class TokenRoleFilter {

    static final String ROLES = "roles";

    private static final Logger log = LoggerFactory.getLogger(TokenRoleFilter.class);

    private TokenRoleFilter() {
    }

    /** Les claims, avec un claim {@code roles} réduit aux rôles admis par le niveau du token. */
    public static Map<String, Object> sanitize(Map<String, Object> claims) {
        if (claims == null || !claims.containsKey(ROLES)) {
            return claims;
        }
        TokenLevel level = TokenLevel.of(claims);
        List<String> kept = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (Object value : claimValues(claims.get(ROLES))) {
            String code = value == null ? null : value.toString();
            if (isAdmitted(code, level)) {
                kept.add(code);
            } else {
                dropped.add(String.valueOf(code));
            }
        }
        if (!dropped.isEmpty()) {
            log.warn("Token : rôles écartés pour le sujet {} (niveau {}) : {}", claims.get("sub"), level, dropped);
        }
        Map<String, Object> sanitized = new LinkedHashMap<>(claims);
        if (kept.isEmpty()) {
            sanitized.remove(ROLES);
        } else {
            sanitized.put(ROLES, List.copyOf(kept));
        }
        return Map.copyOf(sanitized);
    }

    static boolean isAdmitted(String code, TokenLevel level) {
        if (code == null || code.isBlank() || !code.equals(code.strip())) {
            return false;
        }
        Optional<TechnicalRole> technical = TechnicalRole.fromCode(code);
        if (technical.isPresent()) {
            return !technical.get().selfService() && level.admits(technical.get().plan());
        }
        return level == TokenLevel.SPACE && !TenantRoleCodePolicy.isReserved(code);
    }

    /** Un claim liste ou valeur unique, toujours lu comme une collection. */
    static Collection<?> claimValues(Object raw) {
        if (raw instanceof Collection<?> collection) {
            return collection;
        }
        return raw == null ? List.of() : List.of(raw);
    }
}
