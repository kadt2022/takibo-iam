package com.takibo.securitymanagement.infrastructure.security;

import com.takibo.securitycontext.model.TakiboSecurityContext;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class AuthorityFactory {

    private AuthorityFactory() {
    }

    /*
     * Important:
     * - Les permissions/authorities/scope viennent des CLAIMS JWT.
     * - Le ctx sert surtout pour les rôles déclarés côté contexte.
     */
    public static Collection<GrantedAuthority> from(Map<String, Object> claims, TakiboSecurityContext ctx) {
        Set<String> out = new LinkedHashSet<>();

        addRolesFromContext(ctx, out);
        addFromClaim(claims, "roles", out, true);

        addFromClaim(claims, "permissions", out, false);
        addFromClaim(claims, "authorities", out, false);

        addScopesFromClaim(claims, out);

        return out.stream()
                .map(a -> (GrantedAuthority) new SimpleGrantedAuthority(a))
                .toList();
    }

    /*
     * Overload safe : si un appel "context-only" existe quelque part,
     * il ne doit pas produire des 403 silencieux.
     *
     * Choix 1 (strict) : throw pour forcer le passage des claims.
     * Choix 2 (compat) : déléguer vers Map.of() (mais ça perd permissions).
     *
     * Ici on force le correct : claims obligatoires.
     */
    @Deprecated
    public static Collection<GrantedAuthority> from(TakiboSecurityContext ctx) {
        throw new IllegalStateException("Use AuthorityFactory.from(claims, ctx). Context-only loses JWT permissions.");
    }

    private static void addRolesFromContext(TakiboSecurityContext ctx, Set<String> out) {
        if (ctx == null || ctx.subject() == null || ctx.subject().declaredRoles() == null) return;

        for (String r : ctx.subject().declaredRoles()) {
            if (r == null || r.isBlank()) continue;
            out.add(r.startsWith("ROLE_") ? r : "ROLE_" + r);
        }
    }

    private static void addFromClaim(Map<String, Object> claims, String key, Set<String> out, boolean rolePrefix) {
        if (claims == null) return;

        Object v = claims.get(key);

        if (v instanceof Collection<?> c) {
            for (Object o : c) {
                String s = (o == null) ? null : o.toString();
                if (s == null || s.isBlank()) continue;
                out.add(rolePrefix ? (s.startsWith("ROLE_") ? s : "ROLE_" + s) : s);
            }
            return;
        }

        if (v instanceof String s) {
            if (!s.isBlank()) {
                out.add(rolePrefix ? (s.startsWith("ROLE_") ? s : "ROLE_" + s) : s);
            }
        }
    }

    private static void addScopesFromClaim(Map<String, Object> claims, Set<String> out) {
        if (claims == null) return;

        Object v = claims.get("scope");
        if (!(v instanceof String s) || s.isBlank()) return;

        for (String token : s.split("\\s+")) {
            if (token.isBlank()) continue;
            out.add("SCOPE_" + token);
        }
    }
}
