package com.takibo.identitycore.application.rbac.effective.model;

/**
 * Provenance of the actor requesting an effective-permission calculation.
 *
 * <p>PLATFORM_IMPERSONATION deliberately remains absent until RBAC-08.</p>
 */
public enum RbacActorSource {
    HUMAN,
    SERVICE_ACCOUNT,
    SYSTEM
}
