package com.takibo.authorizationserver.infrastructure.springauthserver.token;

/** Preuve signée par TAS : la valeur du JWT et sa durée de vie en secondes. */
public record SignedHumanToken(
        String tokenValue,
        long expiresInSeconds
) {
}
