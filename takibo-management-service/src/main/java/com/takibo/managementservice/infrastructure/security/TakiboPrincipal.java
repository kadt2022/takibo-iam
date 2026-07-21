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
                toUuidOrNull(claims.get("userId")),
                toUuidOrNull(claims.get("accountId")),
                toUuidOrNull(claims.get("orgId")),
                toUuidOrNull(claims.get("spaceId")),
                toStringList(claims.get("roles")),
                toStringList(claims.get("permissions"))
        );
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
