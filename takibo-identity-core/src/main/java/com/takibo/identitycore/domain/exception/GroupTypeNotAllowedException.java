package com.takibo.identitycore.domain.exception;

/**
 * L'action est interdite par politique de gouvernance : la nature du groupe
 * (ex. BUSINESS) n'accepte pas de membership sur cette surface.
 */
public class GroupTypeNotAllowedException extends RuntimeException {
    public GroupTypeNotAllowedException(String message) {
        super(message);
    }
}
