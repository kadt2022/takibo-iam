package com.takibo.identitycore.domain.catalogrbac;

import com.takibo.identitycore.domain.exception.ReservedTenantRoleCodeException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Namespace boundary between tenant-owned roles and the technical RBAC catalog.
 */
public final class TenantRoleCodePolicy {

    private static final List<String> RESERVED_PREFIXES =
            List.of("R_TAKIBO_", "R_ORG_", "R_SPACE_");

    private TenantRoleCodePolicy() {
    }

    public static void requireTenantCode(String code) {
        Objects.requireNonNull(code, "code");
        if (isReserved(code)) {
            throw new ReservedTenantRoleCodeException(code);
        }
    }

    public static boolean isReserved(String code) {
        if (code == null) {
            return false;
        }

        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("PLATFORM")
                || RESERVED_PREFIXES.stream().anyMatch(normalized::startsWith)
                || TechnicalRole.fromCode(normalized).isPresent();
    }
}
