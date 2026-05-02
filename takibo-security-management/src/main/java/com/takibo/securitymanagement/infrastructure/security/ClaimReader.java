package com.takibo.securitymanagement.infrastructure.security;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class ClaimReader {

    private ClaimReader() {
    }

    static UUID readUuid(Map<String, Object> claims, String key) {
        Object raw = claims.get(key);
        if (raw == null) return null;
        if (raw instanceof UUID uuid) return uuid;

        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return null;
        return UUID.fromString(s);
    }

    static String readString(Map<String, Object> claims, String key) {
        Object raw = claims.get(key);
        if (raw == null) return null;

        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    static Set<String> readStringSet(Map<String, Object> claims, String key) {
        Object raw = claims.get(key);
        if (raw == null) return Set.of();

        if (raw instanceof Collection<?> values) {
            return values.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(v -> !v.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }

        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? Set.of() : Set.of(s);
    }
}
