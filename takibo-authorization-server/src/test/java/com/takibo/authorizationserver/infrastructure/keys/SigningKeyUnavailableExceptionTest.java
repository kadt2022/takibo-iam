package com.takibo.authorizationserver.infrastructure.keys;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SigningKeyUnavailableExceptionTest {

    @Test
    void given_a_message_only_then_it_carries_no_cause() {
        SigningKeyUnavailableException exception =
                new SigningKeyUnavailableException("SOME_CODE");

        assertThat(exception.getMessage()).isEqualTo("SOME_CODE");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void given_a_message_and_a_cause_then_both_are_preserved() {
        RuntimeException cause = new RuntimeException("root cause");

        SigningKeyUnavailableException exception =
                new SigningKeyUnavailableException("SOME_CODE", cause);

        assertThat(exception.getMessage()).isEqualTo("SOME_CODE");
        assertThat(exception.getCause()).isSameAs(cause);
    }
}
