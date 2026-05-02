package com.takibo.identitycore.domain.security.policy.password;

import com.takibo.identitycore.domain.exception.PasswordPolicyViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordPolicyValidator {

    private final PasswordPolicy passwordPolicy;

    public void validatePasswordCompliance(String rawPassword) {
        if (!passwordPolicy.isValid(rawPassword)) {
            throw new PasswordPolicyViolationException("Password does not meet policy");
        }
    }
}
