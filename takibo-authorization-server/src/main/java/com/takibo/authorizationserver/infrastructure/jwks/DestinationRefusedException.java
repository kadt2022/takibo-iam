package com.takibo.authorizationserver.infrastructure.jwks;

/** Une destination de récupération JWKS refusée par {@link OutboundDestinationPolicy}. */
public final class DestinationRefusedException extends RuntimeException {

    public DestinationRefusedException(String message) {
        super(message);
    }
}
