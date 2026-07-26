package com.takibo.identitycore.domain.exception;

/**
 * A tenant-owned role tried to use a code reserved for the technical RBAC catalog.
 */
public class ReservedTenantRoleCodeException extends IllegalArgumentException {

    public ReservedTenantRoleCodeException(String code) {
        super("Tenant role code is reserved: " + code);
    }
}
