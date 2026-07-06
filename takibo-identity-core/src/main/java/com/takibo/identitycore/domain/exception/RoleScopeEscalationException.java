package com.takibo.identitycore.domain.exception;

/**
 * On ne délègue jamais au-dessus de son propre scope : un admin de space ne peut
 * ni donner ni retirer un pouvoir de scope ORGANIZATION. Le rôle autorise l'action,
 * la frontière — horizontale ET verticale — limite l'action.
 */
public class RoleScopeEscalationException extends RuntimeException {
    public RoleScopeEscalationException(String message) {
        super(message);
    }
}
