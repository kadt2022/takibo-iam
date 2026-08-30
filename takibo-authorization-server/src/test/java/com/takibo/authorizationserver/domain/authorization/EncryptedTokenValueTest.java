package com.takibo.authorizationserver.domain.authorization;

import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EncryptedTokenValue#seal} et {@link #reveal} sont les deux seuls points d'entrée
 * qu'un adaptateur de persistance doit utiliser pour une colonne {@code *_value}/{@code *_hash}
 * de {@code oauth2_authorization} (TAS-GRANTS-02).
 */
class EncryptedTokenValueTest {

    private static final SecretContext CONTEXT =
            SecretContext.oauth2AuthorizationValue("access_token_value", "auth-1");

    private final SecretCipher cipher = mock(SecretCipher.class);

    @Test
    void given_a_plaintext_when_sealed_then_it_carries_the_ciphertext_and_its_hash() {
        when(cipher.encrypt(CONTEXT, "the-access-token")).thenReturn("v1$key$ciphertext");

        EncryptedTokenValue sealed = EncryptedTokenValue.seal(cipher, CONTEXT, "the-access-token");

        assertThat(sealed.encryptedValue()).isEqualTo("v1$key$ciphertext");
        assertThat(sealed.hash()).isEqualTo(TokenHash.sha256Hex("the-access-token"));
    }

    @Test
    void given_a_sealed_value_when_revealed_then_the_cipher_decrypts_with_the_same_context() {
        when(cipher.decrypt(CONTEXT, "v1$key$ciphertext")).thenReturn("the-access-token");
        EncryptedTokenValue sealed = new EncryptedTokenValue("v1$key$ciphertext", TokenHash.sha256Hex("x"));

        String revealed = sealed.reveal(cipher, CONTEXT);

        assertThat(revealed).isEqualTo("the-access-token");
        verify(cipher).decrypt(CONTEXT, "v1$key$ciphertext");
    }

    @Test
    void given_a_hash_of_the_wrong_length_then_construction_fails_closed() {
        assertThatThrownBy(() -> new EncryptedTokenValue("v1$key$ciphertext", "too-short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void given_a_hash_with_uppercase_characters_then_construction_fails_closed() {
        // Coherent avec la contrainte CHECK de V202601091233 : hexadecimal minuscule uniquement.
        String uppercaseHash = "A".repeat(64);

        assertThatThrownBy(() -> new EncryptedTokenValue("v1$key$ciphertext", uppercaseHash))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void given_a_null_encrypted_value_then_construction_fails_closed() {
        assertThatThrownBy(() -> new EncryptedTokenValue(null, "a".repeat(64)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void given_a_null_plaintext_when_sealing_then_it_fails_closed() {
        assertThatThrownBy(() -> EncryptedTokenValue.seal(cipher, CONTEXT, null))
                .isInstanceOf(NullPointerException.class);
    }
}
