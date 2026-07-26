package com.takibo.identitycore.domain.exception;

/**
 * Refuses an effective-permission calculation whose authority boundary is incomplete
 * or inconsistent with the supplied assignments.
 */
public class EffectivePermissionResolutionException extends RuntimeException {

    public EffectivePermissionResolutionException(String message) {
        super(message);
    }
}
