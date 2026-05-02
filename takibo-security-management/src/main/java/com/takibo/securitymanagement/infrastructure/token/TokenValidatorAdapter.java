package com.takibo.securitymanagement.infrastructure.token;

import java.util.Map;

public interface TokenValidatorAdapter {

    /**
     * Valide un JWT brut et renvoie ses claims sous forme de Map.
     * Lève JwtValidationException si le token est invalide.
     */
    Map<String, Object> validate(String rawToken) throws JwtValidationException;
}
