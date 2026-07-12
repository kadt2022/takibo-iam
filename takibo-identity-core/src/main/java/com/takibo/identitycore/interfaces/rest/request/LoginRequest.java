package com.takibo.identitycore.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Login humain (IAM 31) : {@code orgCode + email + password} — l'organisation
 * identifie le compte. {@code spaceCode} est optionnel et transitoire : présent,
 * le comportement historique (token SPACE) s'applique ; son retrait sera acté
 * par le récit IAM 33.
 */
public record LoginRequest(
        @NotBlank(message = "email is required") String email,
        @NotBlank(message = "password is required") String password,
        @NotBlank(message = "orgCode is required") String orgCode,
        String spaceCode
) {
}
