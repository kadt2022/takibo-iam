package com.takibo.identitycore.application.rbac.effective.model;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;

import java.util.Objects;

/**
 * Canonical permission code emitted by the effective-permission resolver.
 */
public record PermissionCode(String code) implements Comparable<PermissionCode> {

    public PermissionCode {
        Objects.requireNonNull(code, "code");
        if (TechnicalPermission.fromCode(code).isEmpty()) {
            throw new IllegalArgumentException("Unknown canonical permission code: " + code);
        }
    }

    public static PermissionCode from(TechnicalPermission permission) {
        return new PermissionCode(Objects.requireNonNull(permission, "permission").code());
    }

    @Override
    public int compareTo(PermissionCode other) {
        return code.compareTo(other.code);
    }
}
