package com.takibo.identitycore.domain.security.port;


/** Port de hashage de mot de passe (implémenté en infra, ex: Spring Security). */
public interface PasswordHasherCase {
    String hash(String raw);
}
