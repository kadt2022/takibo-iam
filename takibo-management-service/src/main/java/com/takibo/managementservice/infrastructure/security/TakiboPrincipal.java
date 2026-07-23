package com.takibo.managementservice.infrastructure.security;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TakiboPrincipal(
        String subject,
        String username,
        UUID userId,
        UUID accountId,
        UUID orgId,
        UUID spaceId,
        List<String> roles,
        List<String> permissions
) implements Serializable {

    public static TakiboPrincipal fromClaims(Map<String, Object> claims) {
        String subject = asString(claims.getOrDefault("sub", "anonymous"));
        String username = asString(
                claims.getOrDefault("preferred_username",
                        claims.getOrDefault("username", "anonymous"))
        );

        return new TakiboPrincipal(
                subject,
                username,
                toUuidOrNull(claim(claims, "user_id", "userId")),
                toUuidOrNull(claim(claims, "account_id", "accountId")),
                toUuidOrNull(claim(claims, "org_id", "orgId")),
                toUuidOrNull(claim(claims, "space_id", "spaceId")),
                toStringList(claims.get("roles")),
                toStringList(claims.get("permissions"))
        );
    }

    /**
     * Lit un claim en acceptant les deux formes pendant la transition, avec priorité
     * au contrat canonique TAKIBO en snake_case (user_id) sur la forme camelCase (userId).
     */
    private static Object claim(Map<String, Object> claims, String canonical, String legacy) {
        Object value = claims.get(canonical);
        return value != null ? value : claims.get(legacy);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(TakiboPrincipal::asString).toList();
        }
        return List.of(asString(value));
    }

    private static UUID toUuidOrNull(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return value == null ? null : UUID.fromString(value.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
