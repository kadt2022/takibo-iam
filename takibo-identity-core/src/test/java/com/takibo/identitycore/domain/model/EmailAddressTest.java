package com.takibo.identitycore.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La forme canonique de l'email est la clé d'unicité des comptes : deux écritures
 * d'une même adresse doivent converger, une adresse invalide doit être refusée.
 */
class EmailAddressTest {

    @Test
    void canonicalForm_trimsAndLowercases_soVariantsAreEqual() {
        EmailAddress a = new EmailAddress("  Jane.Doe@Example.COM ");
        EmailAddress b = new EmailAddress("jane.doe@example.com");

        assertThat(a.value()).isEqualTo("jane.doe@example.com");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.localPart()).isEqualTo("jane.doe");
        assertThat(a.domain()).isEqualTo("example.com");
    }

    @Test
    void internationalizedDomain_isStoredInPunycode() {
        EmailAddress email = new EmailAddress("user@bücher.de");

        assertThat(email.domain()).isEqualTo("xn--bcher-kva.de");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "no-at-sign",
            "@example.com",
            "user@",
            "user@localhost",
            "us er@example.com",
            "user@exa_mple.com",
            "user@-bad-.com-"
    })
    void malformedAddresses_areRefused(String raw) {
        assertThatThrownBy(() -> new EmailAddress(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlongLocalPart_isRefused() {
        String local = "a".repeat(65);

        assertThatThrownBy(() -> new EmailAddress(local + "@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too long");
    }

    @Test
    void localPartAtTheLimit_isAccepted() {
        String local = "a".repeat(64);

        assertThat(new EmailAddress(local + "@example.com").localPart()).hasSize(64);
    }

    @Test
    void null_isRefused() {
        assertThatThrownBy(() -> new EmailAddress(null)).isInstanceOf(NullPointerException.class);
    }
}
