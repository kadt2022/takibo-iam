package com.takibo.identitycore.domain.security.policy.password;

import com.takibo.identitycore.domain.exception.PasswordPolicyViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator(
            new PasswordPolicy(UUID.randomUUID(), "test", 8, true, true, true, true, 90, 5, true));

    @Test
    void compliantPassword_isAccepted() {
        assertThatCode(() -> validator.validatePasswordCompliance("Abcdefg1!")).doesNotThrowAnyException();
    }

    @Test
    void nonCompliantPassword_isRefused_withTheGenericPolicyMessage() {
        assertThatThrownBy(() -> validator.validatePasswordCompliance("abcdefg1!"))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessage("Password does not meet policy");
    }
}
