package com.takibo.managementservice.application.security;

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

        UUID userId = toUuidOrNull(claims.get("userId"));
        UUID accountId = toUuidOrNull(claims.get("accountId"));
        UUID orgId = toUuidOrNull(claims.get("orgId"));
        UUID spaceId = toUuidOrNull(claims.get("spaceId"));

        List<String> roles = toStringList(claims.get("roles"));
        List<String> permissions = toStringList(claims.get("permissions"));

        return new TakiboPrincipal(
                subject,
                username,
                userId,
                accountId,
                orgId,
                spaceId,
                roles,
                permissions
        );
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(TakiboPrincipal::asString)
                    .toList();
        }
        return List.of(asString(value));
    }

    private static UUID toUuidOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
