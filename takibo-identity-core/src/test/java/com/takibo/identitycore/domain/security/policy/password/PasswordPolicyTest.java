package com.takibo.identitycore.domain.security.policy.password;

import com.takibo.identitycore.domain.exception.PasswordPolicyViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static final String COMPLIANT = "Abcdefg1!";

    private final PasswordPolicy policy = policy(true, true, true, true);

    @Test
    void compliantPassword_isValid_andPassesValidation() {
        assertThat(policy.isValid(COMPLIANT)).isTrue();
        assertThatCode(() -> policy.validate(COMPLIANT)).doesNotThrowAnyException();
    }

    @Test
    void nullPassword_isRefused() {
        assertThat(policy.isValid(null)).isFalse();
        assertThatThrownBy(() -> policy.validate(null))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessage("Password cannot be null");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Abc1!xy   | Password must be at least 8 characters long",
            "abcdefg1! | Password must contain at least one uppercase letter",
            "ABCDEFG1! | Password must contain at least one lowercase letter",
            "Abcdefgh! | Password must contain at least one digit",
            "Abcdefgh1 | Password must contain at least one special character"
    })
    void eachBrokenRule_isRefused_withItsOwnMessage(String password, String message) {
        assertThat(policy.isValid(password)).isFalse();
        assertThatThrownBy(() -> policy.validate(password))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessage(message);
    }

    @Test
    void passwordOf72Bytes_isValid_butOneByteMore_isRefused() {
        String at72 = COMPLIANT + "a".repeat(63);
        String at73 = at72 + "a";
        assertThat(at72.getBytes(StandardCharsets.UTF_8)).hasSize(72);

        assertThat(policy.isValid(at72)).isTrue();
        assertThat(policy.isValid(at73)).isFalse();
        assertThatThrownBy(() -> policy.validate(at73))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessage("Password must not exceed 72 bytes");
    }

    @Test
    void byteLimit_countsEncodedBytes_notCharacters() {
        // 41 caractères mais 73 octets : "é" occupe deux octets en UTF-8.
        String multiByte = COMPLIANT + "é".repeat(32);
        assertThat(multiByte).hasSize(41);
        assertThat(multiByte.getBytes(StandardCharsets.UTF_8)).hasSize(73);

        assertThat(policy.isValid(multiByte)).isFalse();
    }

    @Test
    void lineBreak_isRefused_whileACharacterRuleIsActive() {
        for (String lineBreak : new String[]{"\n", "\r", "\u0085", "\u2028", "\u2029"}) {
            String password = "Abcd" + lineBreak + "efg1!";
            assertThat(policy.isValid(password)).as("U+%04X", (int) lineBreak.charAt(0)).isFalse();
            assertThatThrownBy(() -> policy.validate(password)).hasMessage("Password must not contain line breaks");
        }
    }

    @Test
    void lineBreak_isAccepted_whenNoCharacterRuleIsActive() {
        assertThat(policy(false, false, false, false).isValid("abcdefgh\n")).isTrue();
    }

    @Test
    void disabledCharacterRules_areNotEnforced() {
        PasswordPolicy lenient = policy(false, false, false, false);

        assertThat(lenient.isValid("12345678")).isTrue();
        assertThat(lenient.isValid("abcdefgh")).isTrue();
        assertThat(lenient.isValid("abcdefg")).isFalse();
    }

    @Test
    void firstBrokenRule_isReported_inDeclarationOrder() {
        assertThatThrownBy(() -> policy.validate("abc"))
                .hasMessage("Password must be at least 8 characters long");
    }

    private static PasswordPolicy policy(boolean upper, boolean lower, boolean digit, boolean special) {
        return new PasswordPolicy(UUID.randomUUID(), "test", 8, upper, lower, digit, special, 90, 5, true);
    }
}
