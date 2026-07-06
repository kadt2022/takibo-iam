package com.takibo.identitycore.domain.exception;

/**
 * L'action est interdite par politique de gouvernance : la nature du rôle
 * (ex. BUSINESS) n'est pas assignable sur cette surface.
 */
public class RoleTypeNotAllowedException extends RuntimeException {
    public RoleTypeNotAllowedException(String message) {
        super(message);
    }
}
