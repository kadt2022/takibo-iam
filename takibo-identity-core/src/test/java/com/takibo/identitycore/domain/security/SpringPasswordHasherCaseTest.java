package com.takibo.identitycore.domain.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringPasswordHasherCaseTest {

    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final SpringPasswordHasherCase hasher = new SpringPasswordHasherCase(encoder);

    @Test
    void hash_delegatesToPasswordEncoder() {
        when(encoder.encode("raw")).thenReturn("$2a$hash");

        String result = hasher.hash("raw");

        assertThat(result).isEqualTo("$2a$hash");
        verify(encoder).encode("raw");
    }

    @Test
    void matches_delegatesToPasswordEncoder() {
        when(encoder.matches("raw", "$2a$hash")).thenReturn(true);

        boolean result = hasher.matches("raw", "$2a$hash");

        assertThat(result).isTrue();
        verify(encoder).matches("raw", "$2a$hash");
    }
}
