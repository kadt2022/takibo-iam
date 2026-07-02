package com.takibo.identitycore.domain.security;

import com.takibo.identitycore.domain.security.port.PasswordHasherCase;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SpringPasswordHasherCase implements PasswordHasherCase {
    private final PasswordEncoder encoder;

    public SpringPasswordHasherCase(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String raw) {
        return encoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String hash) {
        return encoder.matches(raw, hash);
    }
}
