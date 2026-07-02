package com.takibo.identitycore.application.auth.model;

/** Preuve signée retournée par l'issuer — opaque pour TIS-CORE. */
public record LoginToken(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
