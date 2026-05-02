package com.takibo.identitycore.domain.security;

import com.takibo.identitycore.domain.security.port.PasswordHasherCase;
import com.takibo.identitycore.domain.vo.PasswordHash;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordHashingComponent {

    private final PasswordHasherCase passwordHasherCase;

    public PasswordHash hashPassword(String rawPassword) {
        return PasswordHash.of(passwordHasherCase.hash(rawPassword), "bcrypt", 1);
    }
}
